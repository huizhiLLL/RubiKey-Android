package com.huizhi.rubikey.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.huizhi.rubikey.MainActivity
import com.huizhi.rubikey.R
import com.huizhi.rubikey.accessibility.RubiKeyAccessibilityService
import com.huizhi.rubikey.cube.CubeBrand
import com.huizhi.rubikey.cube.CubeConnectionStatus
import com.huizhi.rubikey.cube.CubeDevice
import com.huizhi.rubikey.cube.CubeEventSink
import com.huizhi.rubikey.cube.CubeMove
import com.huizhi.rubikey.cube.CubeProtocol
import com.huizhi.rubikey.cube.CubeProtocolRegistry
import com.huizhi.rubikey.cube.CubeSyncState
import com.huizhi.rubikey.mapping.ActionMappingRepository
import com.huizhi.rubikey.mapping.CubeActionRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CubeBleService : Service(), CubeEventSink {
    private val registry = CubeProtocolRegistry.default
    private val discovered = linkedMapOf<String, CubeDevice>()
    private lateinit var mappingRepository: ActionMappingRepository
    private lateinit var actionRouter: CubeActionRouter
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var protocol: CubeProtocol? = null
    private var connectedDevice: CubeDevice? = null

    override fun onCreate() {
        super.onCreate()
        mappingRepository = ActionMappingRepository(this)
        actionRouter = CubeActionRouter({ mappingRepository.load().mapping }, RubiKeyAccessibilityService::submitAction)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCAN -> startScan()
            ACTION_STOP_SCAN -> stopScan()
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_ADDRESS)?.let(::connect)
            ACTION_DISCONNECT -> disconnect("已断开")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disconnect("服务已停止")
        super.onDestroy()
    }

    private fun startScan() {
        if (!hasBluetoothPermissions()) return setError("缺少附近设备权限")
        val adapter = bluetoothAdapter() ?: return setError("设备不支持蓝牙")
        if (!adapter.isEnabled) return setError("蓝牙未开启")
        discovered.clear(); publishDevices()
        scanner = adapter.bluetoothLeScanner ?: return setError("蓝牙扫描器不可用")
        _state.value = CubeBleUiState(scanning = true)
        scanner?.startScan(scanCallback)
    }

    private fun stopScan() {
        runCatching { scanner?.stopScan(scanCallback) }
        scanner = null
        _state.value = _state.value.copy(scanning = false)
    }

    private fun connect(address: String) {
        if (!hasBluetoothPermissions()) return setError("缺少附近设备权限")
        val device = discovered[address] ?: return setError("设备已不在扫描列表中")
        stopScan()
        connectedDevice = device.copy(connectionStatus = CubeConnectionStatus.CONNECTING)
        _state.value = _state.value.copy(connectedDevice = connectedDevice, errorMessage = null)
        startForeground(NOTIFICATION_ID, buildNotification("正在连接 ${device.name}"))
        val adapter = bluetoothAdapter() ?: return disconnect("设备不支持蓝牙")
        gatt = try {
            adapter.getRemoteDevice(address).connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (error: SecurityException) {
            setError("没有蓝牙连接权限"); null
        }
        if (gatt == null) disconnect("无法发起 GATT 连接")
    }

    private fun disconnect(reason: String) {
        stopScan()
        protocol?.clear(); protocol = null
        RubiKeyAccessibilityService.clearPendingActions()
        runCatching { gatt?.disconnect(); gatt?.close() }; gatt = null
        connectedDevice = null
        _state.value = _state.value.copy(connectedDevice = null, errorMessage = reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun hasBluetoothPermissions(): Boolean =
        checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun bluetoothAdapter(): BluetoothAdapter? = getSystemService(BluetoothManager::class.java)?.adapter

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = try { device.name } catch (_: SecurityException) { null } ?: return
            val provider = registry.identify(name) ?: return
            val address = try { device.address } catch (_: SecurityException) { return }
            discovered[address] = CubeDevice(name, address, provider.brand)
            publishDevices()
        }

        override fun onScanFailed(errorCode: Int) = setError("蓝牙扫描失败: $errorCode")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || newState != BluetoothProfile.STATE_CONNECTED) {
                disconnect("蓝牙连接已断开: $status")
            } else if (!gatt.discoverServices()) {
                disconnect("无法发现 GATT 服务")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return disconnect("GATT 服务发现失败: $status")
            val provider = registry.resolve(gatt.services).getOrElse { return disconnect(it.message ?: "协议识别失败") }
            val device = connectedDevice ?: return disconnect("连接设备状态已丢失")
            val service = provider.findService(gatt.services) ?: return disconnect("协议服务已丢失")
            protocol = provider.create(this@CubeBleService)
            if (!protocol!!.start(gatt, service, device)) {
                disconnect("协议启动失败")
            } else {
                connectedDevice = device.copy(connectionStatus = CubeConnectionStatus.CONNECTED)
                _state.value = _state.value.copy(connectedDevice = connectedDevice)
                startForeground(NOTIFICATION_ID, buildNotification("已连接 ${device.name}"))
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) { protocol?.onCharacteristicChanged(characteristic) }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { protocol?.onCharacteristicWrite(characteristic, status) }
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) { protocol?.onDescriptorWrite(descriptor, status) }
    }

    override fun onMove(move: CubeMove, elapsedMs: Int) {
        _state.value = _state.value.copy(lastMove = move, lastMoveElapsedMs = elapsedMs)
        actionRouter.route(move)
    }
    override fun onBatteryChanged(percent: Int) { _state.value = _state.value.copy(batteryPercent = percent.coerceIn(0, 100)) }
    override fun onSyncStateChanged(state: CubeSyncState) { _state.value = _state.value.copy(syncState = state) }
    override fun onProtocolError(message: String, cause: Throwable?) { setError(message) }

    private fun publishDevices() { _state.value = _state.value.copy(devices = discovered.values.toList()) }
    private fun setError(message: String) { _state.value = _state.value.copy(errorMessage = message, scanning = false) }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.connected_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.connected_notification_title))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_SCAN = "com.huizhi.rubikey.action.SCAN"
        const val ACTION_STOP_SCAN = "com.huizhi.rubikey.action.STOP_SCAN"
        const val ACTION_CONNECT = "com.huizhi.rubikey.action.CONNECT"
        const val ACTION_DISCONNECT = "com.huizhi.rubikey.action.DISCONNECT"
        const val EXTRA_ADDRESS = "address"
        private const val CHANNEL_ID = "cube_connection"
        private const val NOTIFICATION_ID = 1001
        private val _state = MutableStateFlow(CubeBleUiState())
        val state = _state.asStateFlow()

        fun command(context: Context, action: String, address: String? = null) {
            val intent = Intent(context, CubeBleService::class.java).setAction(action)
            if (address != null) intent.putExtra(EXTRA_ADDRESS, address)
            context.startService(intent)
        }
    }
}

data class CubeBleUiState(
    val scanning: Boolean = false,
    val devices: List<CubeDevice> = emptyList(),
    val connectedDevice: CubeDevice? = null,
    val lastMove: CubeMove? = null,
    val lastMoveElapsedMs: Int? = null,
    val batteryPercent: Int? = null,
    val syncState: CubeSyncState? = null,
    val errorMessage: String? = null,
)
