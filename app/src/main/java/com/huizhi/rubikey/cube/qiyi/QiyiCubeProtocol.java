/*
 * Derived from DCTimer-BLE's QiyiCubeProtocol.java (GPL-3.0-or-later).
 * Timing, full cube state, 3D and Activity dependencies were removed.
 */
package com.huizhi.rubikey.cube.qiyi;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;
import com.huizhi.rubikey.cube.CubeDevice;
import com.huizhi.rubikey.cube.CubeEventSink;
import com.huizhi.rubikey.cube.CubeMove;
import com.huizhi.rubikey.cube.CubeProtocol;
import com.huizhi.rubikey.cube.CubeSyncState;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/* CubeBleService checks BLUETOOTH_CONNECT before entering protocol callbacks. */
@SuppressLint({"MissingPermission", "GetInstance"})
public final class QiyiCubeProtocol implements CubeProtocol {
    public static final UUID SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    public static final UUID CUBE_UUID = UUID.fromString("0000fff6-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE_UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte[] AES_KEY = {87, (byte) 177, (byte) 249, (byte) 171, (byte) 205, 90,
            (byte) 232, (byte) 167, (byte) 156, (byte) 185, (byte) 140, (byte) 231, 87, (byte) 140, 81, 8};
    private static final int[] MOVE_AXIS_MAP = {4, 1, 3, 0, 2, 5};
    private static final int REQUIRED_MTU = 64;
    private static final int HISTORY_COUNT = 11;
    private static final int HISTORY_START = 36;
    private static final int HISTORY_SIZE = 5;
    private static final int FALLBACK_DELAY_MS = 1500;
    private static final int HELLO_RETRY_MS = 3000;
    private static final int WRITE_RETRY_MS = 80;
    private static final int MAX_HELLO_ATTEMPTS = 2;

    private final CubeEventSink eventSink;
    private final ArrayDeque<byte[]> requestQueue = new ArrayDeque<>();
    private final Handler handler;
    private final Cipher encryptCipher;
    private final Cipher decryptCipher;
    private final Runnable sendNextRequest = this::sendNextRequest;
    private final Runnable fallbackHello = this::sendFallbackHello;
    private final Runnable retryHello = this::retryHello;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic cubeCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;
    private boolean notificationsReady;
    private boolean mtuReady;
    private boolean writePending;
    private boolean writeWithoutResponse;
    private boolean helloReceived;
    private boolean fallbackHelloSent;
    private boolean protocolMismatch;
    private String activeMac;
    private String fallbackMac;
    private long lastTimestamp = -1;
    private int helloAttempts;
    private int nonProtocolMessages;

    public QiyiCubeProtocol(CubeEventSink eventSink) {
        this.eventSink = eventSink;
        handler = new Handler(Looper.getMainLooper());
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY, "AES");
            encryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, key);
            decryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
            decryptCipher.init(Cipher.DECRYPT_MODE, key);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("QiYi AES 初始化失败", error);
        }
    }

    public static boolean matchesDeviceName(String name) {
        if (name == null) return false;
        String normalized = name.trim().toUpperCase(Locale.US);
        return normalized.startsWith("QY-QYSC") || normalized.startsWith("XMD-TORNADOV4-I");
    }

    @Override public boolean start(BluetoothGatt gatt, BluetoothGattService service, CubeDevice device) {
        clear();
        this.gatt = gatt;
        activeMac = normalizeMac(device.getProtocolAddress());
        fallbackMac = fallbackMac(device.getName());
        cubeCharacteristic = service == null ? null : service.getCharacteristic(CUBE_UUID);
        if (cubeCharacteristic == null) return fail("QiYi 缺少通知特征", null);
        writeCharacteristic = supportsWrite(cubeCharacteristic) ? cubeCharacteristic : service.getCharacteristic(WRITE_UUID);
        if (writeCharacteristic == null) return fail("QiYi 缺少写入特征", null);
        eventSink.onSyncStateChanged(CubeSyncState.WAITING_FOR_INITIAL_STATE);
        mtuReady = false;
        if (!gatt.requestMtu(REQUIRED_MTU)) {
            mtuReady = true;
            setupNotifications();
        }
        return true;
    }

    @Override public void clear() {
        handler.removeCallbacks(sendNextRequest);
        handler.removeCallbacks(fallbackHello);
        handler.removeCallbacks(retryHello);
        requestQueue.clear();
        gatt = null;
        cubeCharacteristic = null;
        writeCharacteristic = null;
        notificationsReady = false;
        mtuReady = false;
        writePending = false;
        writeWithoutResponse = false;
        helloReceived = false;
        fallbackHelloSent = false;
        protocolMismatch = false;
        activeMac = null;
        fallbackMac = null;
        lastTimestamp = -1;
        helloAttempts = 0;
        nonProtocolMessages = 0;
    }

    @Override public void onMtuChanged(int mtu, int status) {
        mtuReady = true;
        if (!notificationsReady) setupNotifications();
    }

    @Override public void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        if (descriptor != null && descriptor.getCharacteristic() != null
                && CUBE_UUID.equals(descriptor.getCharacteristic().getUuid())) onNotificationsEnabled();
    }

    @Override public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (characteristic == null || writeCharacteristic == null
                || !writeCharacteristic.getUuid().equals(characteristic.getUuid())) return;
        writePending = false;
        if (status != BluetoothGatt.GATT_SUCCESS) fail("QiYi 请求写入失败: " + status, null);
        sendNextRequest();
    }

    @Override public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || !CUBE_UUID.equals(characteristic.getUuid())) return;
        try {
            parseMessage(characteristic.getValue());
        } catch (Exception error) {
            fail("QiYi 数据解析失败", error);
        }
    }

    private void setupNotifications() {
        if (gatt == null || cubeCharacteristic == null) return;
        if (!gatt.setCharacteristicNotification(cubeCharacteristic, true)) {
            fail("QiYi 无法开启通知", null);
            return;
        }
        BluetoothGattDescriptor descriptor = cubeCharacteristic.getDescriptor(CCCD);
        if (descriptor == null) {
            onNotificationsEnabled();
            return;
        }
        int properties = cubeCharacteristic.getProperties();
        descriptor.setValue((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                && (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0
                ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!gatt.writeDescriptor(descriptor)) onNotificationsEnabled();
    }

    private void onNotificationsEnabled() {
        if (notificationsReady) return;
        notificationsReady = true;
        String mac = activeMac != null ? activeMac : fallbackMac;
        if (mac == null) {
            fail("QiYi 缺少可用协议地址", null);
            return;
        }
        activeMac = mac;
        enqueueHello("初始化");
        if (fallbackMac != null && !fallbackMac.equalsIgnoreCase(activeMac)) {
            handler.postDelayed(fallbackHello, FALLBACK_DELAY_MS);
        }
        handler.postDelayed(retryHello, HELLO_RETRY_MS);
    }

    private void sendFallbackHello() {
        if (helloReceived || fallbackHelloSent || fallbackMac == null || protocolMismatch
                || fallbackMac.equalsIgnoreCase(activeMac)) return;
        fallbackHelloSent = true;
        activeMac = fallbackMac;
        enqueueHello("备用 MAC");
        handler.postDelayed(retryHello, HELLO_RETRY_MS);
    }

    private void retryHello() {
        if (!helloReceived && !protocolMismatch && helloAttempts < MAX_HELLO_ATTEMPTS) enqueueHello("超时重试");
    }

    private void enqueueHello(String reason) {
        if (activeMac == null || protocolMismatch || helloAttempts >= MAX_HELLO_ATTEMPTS) return;
        helloAttempts++;
        enqueueMessage(buildHelloContent(activeMac), true);
    }

    private void enqueueMessage(byte[] content, boolean priority) {
        byte[] message = buildMessage(content);
        if (priority) requestQueue.offerFirst(message); else requestQueue.offer(message);
        sendNextRequest();
    }

    private void sendNextRequest() {
        if (!notificationsReady || !mtuReady || writePending || gatt == null
                || writeCharacteristic == null || requestQueue.isEmpty()) return;
        byte[] request = requestQueue.poll();
        try {
            byte[] encoded = encrypt(request);
            int properties = writeCharacteristic.getProperties();
            writeWithoutResponse = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
            writeCharacteristic.setWriteType(writeWithoutResponse
                    ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            writeCharacteristic.setValue(encoded);
            if (!gatt.writeCharacteristic(writeCharacteristic)) {
                requestQueue.offerFirst(request);
                handler.postDelayed(sendNextRequest, WRITE_RETRY_MS);
                return;
            }
            writePending = !writeWithoutResponse;
            if (writeWithoutResponse && !requestQueue.isEmpty()) handler.post(sendNextRequest);
        } catch (GeneralSecurityException error) {
            fail("QiYi 请求加密失败", error);
        }
    }

    private void parseMessage(byte[] encrypted) throws GeneralSecurityException {
        if (encrypted == null || encrypted.length == 0 || encrypted.length % 16 != 0) {
            fail("QiYi 收到非法数据包长度", null);
            return;
        }
        byte[] decoded = decrypt(encrypted);
        int messageLength = decoded.length < 2 ? 0 : decoded[1] & 0xff;
        if (messageLength < 3 || messageLength > decoded.length) {
            fail("QiYi 消息长度非法", null);
            return;
        }
        byte[] message = Arrays.copyOf(decoded, messageLength);
        if (crc16Modbus(message, message.length) != 0) {
            fail("QiYi 消息 CRC 校验失败", null);
            return;
        }
        if ((message[0] & 0xff) != 0xfe) {
            handleNonProtocolMessage(message);
            return;
        }
        nonProtocolMessages = 0;
        int opcode = message[2] & 0xff;
        long timestamp = message.length >= 7 ? readUint32(message, 3) : 0;
        if (opcode == 0x02) handleInitial(message, timestamp, true);
        else if (opcode == 0x03) handleStateChange(message, timestamp);
        else if (opcode == 0x04) handleInitial(message, timestamp, false);
    }

    private void handleInitial(byte[] message, long timestamp, boolean acknowledge) {
        markHelloReceived();
        if (acknowledge) sendAck(message);
        if (message.length < 36 || !validFacelets(message, 7)) {
            fail("QiYi 初始状态数据非法", null);
            return;
        }
        lastTimestamp = timestamp;
        eventSink.onBatteryChanged(message[35] & 0xff);
        eventSink.onSyncStateChanged(CubeSyncState.SYNCHRONIZED);
    }

    private void handleStateChange(byte[] message, long timestamp) {
        markHelloReceived();
        sendAck(message);
        if (message.length < 36 || !validFacelets(message, 7)) {
            fail("QiYi 状态数据非法", null);
            return;
        }
        if (lastTimestamp < 0) {
            handleInitial(message, timestamp, false);
            return;
        }
        MoveSample[] moves = collectStateChangeMoves(message, lastTimestamp, timestamp);
        for (MoveSample sample : moves) {
            CubeMove move = convertMove(sample.move);
            if (move != null) eventSink.onMove(move, calculateDelta(sample.timestamp));
        }
        // Future history belongs to visual prediction in DCTimer. RubiKey waits until it becomes current.
        lastTimestamp = Math.max(lastTimestamp, timestamp);
        eventSink.onBatteryChanged(message[35] & 0xff);
    }

    private void markHelloReceived() {
        helloReceived = true;
        handler.removeCallbacks(fallbackHello);
        handler.removeCallbacks(retryHello);
    }

    private void sendAck(byte[] message) {
        if (message.length >= 7) enqueueMessage(Arrays.copyOfRange(message, 2, 7), false);
    }

    private int calculateDelta(long timestamp) {
        if (lastTimestamp < 0) {
            lastTimestamp = timestamp;
            return 0;
        }
        long raw = timestamp - lastTimestamp;
        lastTimestamp = timestamp;
        return (int) Math.max(0, Math.min(Math.round(raw / 1.6f), 0xffff));
    }

    private void handleNonProtocolMessage(byte[] message) {
        nonProtocolMessages++;
        if (!helloReceived && (message[0] & 0xff) == 0xcc && nonProtocolMessages >= 3) {
            protocolMismatch = true;
            requestQueue.clear();
            handler.removeCallbacks(fallbackHello);
            handler.removeCallbacks(retryHello);
            handler.removeCallbacks(sendNextRequest);
            fail("QiYi 设备返回不兼容的 0xCC 协议", null);
        }
    }

    static final class MoveSample {
        final int move;
        final long timestamp;
        MoveSample(int move, long timestamp) { this.move = move; this.timestamp = timestamp; }
    }

    static MoveSample[] collectStateChangeMoves(byte[] message, long previousTimestamp, long frameTimestamp) {
        if (message == null || message.length < 35) return new MoveSample[0];
        List<MoveSample> candidates = new ArrayList<>();
        candidates.add(new MoveSample(message[34] & 0xff, readUint32(message, 3)));
        for (int i = 0; i < HISTORY_COUNT; i++) {
            int offset = HISTORY_START + i * HISTORY_SIZE;
            if (offset + HISTORY_SIZE > message.length) break;
            if (!emptyHistorySlot(message, offset)) candidates.add(new MoveSample(message[offset + 4] & 0xff, readUint32(message, offset)));
        }
        candidates.sort(Comparator.comparingLong(item -> item.timestamp));
        List<MoveSample> result = new ArrayList<>();
        for (MoveSample candidate : candidates) {
            if (candidate.timestamp <= previousTimestamp || candidate.timestamp > frameTimestamp
                    || convertMove(candidate.move) == null) continue;
            boolean duplicate = false;
            for (MoveSample existing : result) {
                if (existing.move == candidate.move && existing.timestamp == candidate.timestamp) duplicate = true;
            }
            if (!duplicate) result.add(candidate);
        }
        return result.toArray(new MoveSample[0]);
    }

    static CubeMove convertMove(int rawMove) {
        if (rawMove <= 0) return null;
        int axisIndex = (rawMove - 1) >> 1;
        if (axisIndex >= MOVE_AXIS_MAP.length) return null;
        int stableIndex = MOVE_AXIS_MAP[axisIndex] * 2 + ((rawMove & 1) == 0 ? 0 : 1);
        return CubeMove.Companion.fromStableIndex(stableIndex);
    }

    static int crc16Modbus(byte[] data, int length) {
        int crc = 0xffff;
        for (int i = 0; i < length; i++) {
            crc ^= data[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xa001 : crc >> 1;
        }
        return crc & 0xffff;
    }

    private static boolean validFacelets(byte[] message, int offset) {
        if (offset + 27 > message.length) return false;
        for (int i = 0; i < 54; i++) if (((message[offset + (i >> 1)] & 0xff) >> ((i & 1) * 4) & 0x0f) > 5) return false;
        return true;
    }

    private static boolean emptyHistorySlot(byte[] message, int offset) {
        for (int i = 0; i < HISTORY_SIZE; i++) if ((message[offset + i] & 0xff) != 0xff) return false;
        return true;
    }

    private static long readUint32(byte[] data, int offset) {
        return ((data[offset] & 0xffL) << 24) | ((data[offset + 1] & 0xffL) << 16)
                | ((data[offset + 2] & 0xffL) << 8) | (data[offset + 3] & 0xffL);
    }

    private byte[] buildHelloContent(String mac) {
        byte[] macBytes = parseMac(mac);
        byte[] content = {0x00, 0x6b, 0x01, 0x00, 0x00, 0x22, 0x06, 0x00, 0x02, 0x08, 0x00,
                macBytes[5], macBytes[4], macBytes[3], macBytes[2], macBytes[1], macBytes[0]};
        return content;
    }

    private byte[] buildMessage(byte[] content) {
        int messageLength = content.length + 4;
        byte[] message = new byte[((messageLength + 15) / 16) * 16];
        message[0] = (byte) 0xfe;
        message[1] = (byte) messageLength;
        System.arraycopy(content, 0, message, 2, content.length);
        int crc = crc16Modbus(message, content.length + 2);
        message[content.length + 2] = (byte) crc;
        message[content.length + 3] = (byte) (crc >> 8);
        return message;
    }

    private byte[] encrypt(byte[] value) throws GeneralSecurityException { return cryptBlocks(value, encryptCipher); }
    private byte[] decrypt(byte[] value) throws GeneralSecurityException { return cryptBlocks(value, decryptCipher); }

    private static byte[] cryptBlocks(byte[] value, Cipher cipher) throws GeneralSecurityException {
        byte[] result = new byte[value.length];
        for (int offset = 0; offset < value.length; offset += 16) {
            byte[] block = cipher.doFinal(Arrays.copyOfRange(value, offset, offset + 16));
            System.arraycopy(block, 0, result, offset, 16);
        }
        return result;
    }

    private static boolean supportsWrite(BluetoothGattCharacteristic characteristic) {
        int properties = characteristic.getProperties();
        return (properties & (BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    private static String normalizeMac(String mac) {
        if (mac == null) return null;
        String normalized = mac.trim().toUpperCase(Locale.US);
        return normalized.matches("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$") ? normalized : null;
    }

    private static String fallbackMac(String name) {
        if (!matchesDeviceName(name)) return null;
        String normalized = name.trim().toUpperCase(Locale.US);
        if (normalized.length() < 4) return null;
        String suffix = normalized.substring(normalized.length() - 4);
        return suffix.matches("[0-9A-F]{4}") ? "CC:A3:00:00:" + suffix.substring(0, 2) + ":" + suffix.substring(2) : null;
    }

    private static byte[] parseMac(String mac) {
        String[] parts = mac.split(":");
        byte[] result = new byte[6];
        for (int i = 0; i < 6; i++) result[i] = (byte) Integer.parseInt(parts[i], 16);
        return result;
    }

    private boolean fail(String message, Throwable cause) {
        eventSink.onProtocolError(message, cause);
        return false;
    }
}
