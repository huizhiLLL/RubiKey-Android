package com.huizhi.rubikey.cube

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanResult
import com.huizhi.rubikey.cube.qiyi.QiyiCubeProtocol
import java.util.Locale

class QiyiProtocolProvider : CubeProtocolProvider {
    override val brand = CubeBrand.QIYI

    override fun matchesDevice(name: String?): Boolean = QiyiCubeProtocol.matchesDeviceName(name)

    override fun matchesServices(services: List<BluetoothGattService>): Boolean = findService(services) != null

    override fun findService(services: List<BluetoothGattService>): BluetoothGattService? = services.firstOrNull { service ->
        val cube = service.getCharacteristic(QiyiCubeProtocol.CUBE_UUID)
        val fallbackWrite = service.getCharacteristic(QiyiCubeProtocol.WRITE_UUID)
        service.uuid == QiyiCubeProtocol.SERVICE_UUID && cube != null ||
            cube != null && (supportsWrite(cube) || fallbackWrite != null)
    }

    override fun resolveProtocolAddress(result: ScanResult, connectionAddress: String): String {
        val bytes = result.scanRecord?.manufacturerSpecificData?.get(MANUFACTURER_ID)
        return protocolAddressFromManufacturerData(bytes) ?: connectionAddress
    }

    override fun create(eventSink: CubeEventSink): CubeProtocol = QiyiCubeProtocol(eventSink)

    private fun supportsWrite(characteristic: BluetoothGattCharacteristic): Boolean =
        characteristic.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

    companion object {
        private const val MANUFACTURER_ID = 0x0504

        @JvmStatic
        fun protocolAddressFromManufacturerData(bytes: ByteArray?): String? {
            if (bytes == null || bytes.size < 6) return null
            return (5 downTo 0).joinToString(":") { index ->
                String.format(Locale.US, "%02X", bytes[index].toInt() and 0xff)
            }
        }
    }
}
