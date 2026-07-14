/*
 * Derived from DCTimer-BLE's Moyu32CubeProtocol.java (GPL-3.0-or-later).
 * The Android activity, timing, 3D state and gyro UI dependencies were removed.
 */
package com.huizhi.rubikey.cube.moyu;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import com.huizhi.rubikey.cube.CubeDevice;
import com.huizhi.rubikey.cube.CubeEventSink;
import com.huizhi.rubikey.cube.CubeMove;
import com.huizhi.rubikey.cube.CubeProtocol;
import com.huizhi.rubikey.cube.CubeStateTracker;
import com.huizhi.rubikey.cube.CubeSyncState;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;

public final class Moyu32CubeProtocol implements CubeProtocol {
    public static final UUID SERVICE_UUID = UUID.fromString("0783b03e-7735-b5a0-1760-a305d2795cb0");
    public static final UUID READ_UUID = UUID.fromString("0783b03e-7735-b5a0-1760-a305d2795cb1");
    public static final UUID WRITE_UUID = UUID.fromString("0783b03e-7735-b5a0-1760-a305d2795cb2");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final CubeEventSink eventSink;
    private final CubeStateTracker stateTracker = new CubeStateTracker();
    private final Moyu32Cipher cipher = new Moyu32Cipher();
    private final ArrayDeque<byte[]> requestQueue = new ArrayDeque<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic readCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;
    private String deviceName;
    private String protocolAddress;
    private boolean notificationsReady;
    private boolean writePending;
    private boolean fallbackTried;
    private int previousMoveCounter = -1;

    public Moyu32CubeProtocol(CubeEventSink eventSink) { this.eventSink = eventSink; }

    @Override public boolean start(BluetoothGatt gatt, BluetoothGattService service, CubeDevice device) {
        clear();
        this.gatt = gatt;
        this.deviceName = device.getName().trim();
        this.protocolAddress = device.getAddress();
        readCharacteristic = service.getCharacteristic(READ_UUID);
        writeCharacteristic = service.getCharacteristic(WRITE_UUID);
        if (readCharacteristic == null || writeCharacteristic == null) {
            error("MoYu32 缺少必要的 GATT 特征", null); return false;
        }
        try { cipher.init(protocolAddress); } catch (Exception e) { error("MoYu32 地址初始化失败", e); return false; }
        if (!gatt.setCharacteristicNotification(readCharacteristic, true)) {
            error("MoYu32 无法开启通知", null); return false;
        }
        BluetoothGattDescriptor descriptor = readCharacteristic.getDescriptor(CCCD_UUID);
        if (descriptor == null) {
            onNotificationsEnabled();
        } else {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(descriptor)) onNotificationsEnabled();
        }
        eventSink.onSyncStateChanged(CubeSyncState.WAITING_FOR_INITIAL_STATE);
        return true;
    }

    @Override public void clear() {
        requestQueue.clear(); stateTracker.clear(); gatt = null; readCharacteristic = null; writeCharacteristic = null;
        deviceName = null; protocolAddress = null; notificationsReady = false; writePending = false;
        fallbackTried = false; previousMoveCounter = -1;
    }

    @Override public void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        if (descriptor != null && descriptor.getCharacteristic() != null && READ_UUID.equals(descriptor.getCharacteristic().getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS) onNotificationsEnabled();
            else error("MoYu32 通知描述符写入失败: " + status, null);
        }
    }

    @Override public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (characteristic == null || !WRITE_UUID.equals(characteristic.getUuid())) return;
        writePending = false;
        if (status != BluetoothGatt.GATT_SUCCESS) error("MoYu32 请求写入失败: " + status, null);
        sendNextRequest();
    }

    @Override public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || !READ_UUID.equals(characteristic.getUuid())) return;
        byte[] value = characteristic.getValue();
        if (value == null || value.length < 16) { error("MoYu32 收到非法数据包长度", null); return; }
        try { parseData(value); } catch (Exception e) { error("MoYu32 数据解析失败", e); }
    }

    private void onNotificationsEnabled() {
        if (notificationsReady) return;
        notificationsReady = true;
        int[] requests = {161, 163, 164, 161, 163, 164};
        for (int opcode : requests) enqueueSimpleRequest(opcode);
        enqueueGyroEnableRequest();
        enqueueSimpleRequest(163);
    }

    private void enqueueSimpleRequest(int opcode) {
        byte[] request = new byte[20]; request[0] = (byte) opcode; requestQueue.offer(request); sendNextRequest();
    }

    private void enqueueGyroEnableRequest() {
        byte[] request = new byte[20]; request[0] = (byte) 172; request[2] = 1; requestQueue.offer(request); sendNextRequest();
    }

    private void sendNextRequest() {
        if (!notificationsReady || writePending || gatt == null || writeCharacteristic == null || requestQueue.isEmpty()) return;
        byte[] request = requestQueue.poll();
        try {
            writeCharacteristic.setValue(cipher.encode(request));
            writePending = gatt.writeCharacteristic(writeCharacteristic);
            if (!writePending) error("MoYu32 请求未能进入写入队列", null);
        } catch (GeneralSecurityException e) { error("MoYu32 请求加密失败", e); }
    }

    private void parseData(byte[] value) throws GeneralSecurityException {
        byte[] decoded = cipher.decode(value);
        if (decoded.length < 16) { error("MoYu32 解密包长度不足", null); return; }
        String bits = toBits(decoded);
        int messageType = bitsAt(bits, 0, 8);
        switch (messageType) {
            case 163: handleInitialState(bits); break;
            case 164: eventSink.onBatteryChanged(bitsAt(bits, 8, 8)); break;
            case 165: handleMoves(bits); break;
            case 161:
            case 171: break; // POC 不消费陀螺仪数据。
            default:
                if (previousMoveCounter == -1 && tryFallbackCipher()) return;
                error("未知 MoYu32 消息类型: " + messageType, null);
        }
    }

    private void handleInitialState(String bits) {
        if (previousMoveCounter != -1) return;
        int counter = bitsAt(bits, 152, 8);
        previousMoveCounter = counter;
        stateTracker.synchronize(counter);
        eventSink.onSyncStateChanged(CubeSyncState.SYNCHRONIZED);
    }

    private void handleMoves(String bits) {
        int counter = bitsAt(bits, 88, 8);
        if (previousMoveCounter == -1 || counter == previousMoveCounter) return;
        int difference = (counter - previousMoveCounter) & 0xff;
        previousMoveCounter = counter;
        if (difference > 5) difference = 5;
        int[] offsets = new int[5]; int[] rawMoves = new int[5];
        for (int i = 0; i < 5; i++) { offsets[i] = bitsAt(bits, 8 + i * 16, 16); rawMoves[i] = bitsAt(bits, 96 + i * 5, 5); }
        for (int i = difference - 1; i >= 0; i--) {
            CubeMove move = CubeMove.Companion.fromMoyuRaw(rawMoves[i]);
            if (move == null) { error("MoYu32 收到未知转动编号: " + rawMoves[i], null); continue; }
            stateTracker.apply(move, counter);
            eventSink.onMove(move, offsets[i]);
        }
    }

    private boolean tryFallbackCipher() {
        if (fallbackTried || deviceName == null || !deviceName.matches("^WCU_MY32_[0-9A-Fa-f]{4}$")) return false;
        fallbackTried = true;
        String suffix = deviceName.substring(deviceName.length() - 4).toUpperCase(Locale.US);
        try {
            protocolAddress = "CF:30:16:00:" + suffix.substring(0, 2) + ":" + suffix.substring(2, 4);
            cipher.init(protocolAddress); previousMoveCounter = -1; requestQueue.clear(); writePending = false;
            enqueueSimpleRequest(163); enqueueSimpleRequest(164); return true;
        } catch (Exception e) { error("MoYu32 备用地址初始化失败", e); return false; }
    }

    private int bitsAt(String data, int start, int length) { return Integer.parseInt(data.substring(start, start + length), 2); }
    private String toBits(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 8);
        for (byte item : value) result.append(String.format(Locale.US, "%8s", Integer.toBinaryString(item & 0xff)).replace(' ', '0'));
        return result.toString();
    }
    private void error(String message, Throwable cause) { eventSink.onProtocolError(message, cause); }
}
