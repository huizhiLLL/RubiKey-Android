/*
 * Derived from DCTimer-BLE's GanCubeProtocol.java (GPL-3.0-or-later).
 * Timing, full cube state, 3D and Activity dependencies were removed.
 */
package com.huizhi.rubikey.cube.gan;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import com.huizhi.rubikey.cube.CubeDevice;
import com.huizhi.rubikey.cube.CubeEventSink;
import com.huizhi.rubikey.cube.CubeMove;
import com.huizhi.rubikey.cube.CubeProtocol;
import com.huizhi.rubikey.cube.CubeSyncState;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GanCubeProtocol implements CubeProtocol {
    public static final UUID SERVICE_UUID_V2 = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dc4179");
    public static final UUID SERVICE_UUID_V3 = UUID.fromString("8653000a-43e6-47b7-9cb0-5fc21d4ae340");
    public static final UUID SERVICE_UUID_V4 = UUID.fromString("00000010-0000-fff7-fff6-fff5fff4fff0");
    public static final Set<UUID> SERVICE_UUIDS = Set.of(SERVICE_UUID_V2, SERVICE_UUID_V3, SERVICE_UUID_V4);
    private static final UUID V2_READ = UUID.fromString("28be4cb6-cd67-11e9-a32f-2a2ae2dbcce4");
    private static final UUID V2_WRITE = UUID.fromString("28be4a4a-cd67-11e9-a32f-2a2ae2dbcce4");
    private static final UUID V3_READ = UUID.fromString("8653000b-43e6-47b7-9cb0-5fc21d4ae340");
    private static final UUID V3_WRITE = UUID.fromString("8653000c-43e6-47b7-9cb0-5fc21d4ae340");
    private static final UUID V4_READ = UUID.fromString("0000fff6-0000-1000-8000-00805f9b34fb");
    private static final UUID V4_WRITE = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte[] BASE_KEY = {1, 2, 66, 40, 49, (byte) 145, 22, 7, 32, 5, 24, 84, 66, 17, 18, 83};
    private static final byte[] BASE_IV = {17, 3, 50, 40, 33, 1, 118, 39, 32, (byte) 149, 120, 20, 50, 18, 2, 67};
    private static final byte[] AICUBE_KEY = {5, 18, 2, 69, 2, 1, 41, 86, 18, 120, 18, 118, (byte) 129, 1, 8, 3};
    private static final byte[] AICUBE_IV = {1, 68, 40, 6, (byte) 134, 33, 34, 40, 81, 5, 8, 49, (byte) 130, 2, 33, 6};
    private static final int[] AXIS_MASKS = {2, 32, 8, 1, 16, 4};

    private final CubeEventSink eventSink;
    private final GanCubeCipher cipher = new GanCubeCipher();
    private final ArrayDeque<byte[]> requestQueue = new ArrayDeque<>();
    private final Map<Integer, MoveEvent> pendingMoves = new HashMap<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic readCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;
    private Variant variant;
    private boolean notificationsReady;
    private boolean writePending;
    private int previousMoveCounter = -1;
    private int latestMoveCounter = -1;
    private int requestedHistoryEnd = -1;
    private long lastDeviceTimestamp = -1;
    private long lastLocalTimestamp = -1;

    private enum Variant { V2, V3, V4 }

    private static final class MoveEvent {
        final CubeMove move;
        final Long deviceTimestamp;
        final long localTimestamp;

        MoveEvent(CubeMove move, Long deviceTimestamp, long localTimestamp) {
            this.move = move;
            this.deviceTimestamp = deviceTimestamp;
            this.localTimestamp = localTimestamp;
        }
    }

    public GanCubeProtocol(CubeEventSink eventSink) {
        this.eventSink = eventSink;
    }

    public static boolean matchesDeviceName(String name) {
        if (name == null) return false;
        String normalized = name.trim().toUpperCase(Locale.US);
        return !normalized.startsWith("GANBOT-")
                && (normalized.startsWith("GAN") || normalized.startsWith("MG") || normalized.startsWith("AICUBE"));
    }

    @Override public boolean start(BluetoothGatt gatt, BluetoothGattService service, CubeDevice device) {
        clear();
        this.gatt = gatt;
        variant = resolveVariant(service == null ? null : service.getUuid());
        if (variant == null) return fail("GAN 无法识别协议版本", null);
        try {
            boolean aiCube = variant == Variant.V2 && device.getName().trim().toUpperCase(Locale.US).startsWith("AICUBE");
            cipher.init(aiCube ? AICUBE_KEY : BASE_KEY, aiCube ? AICUBE_IV : BASE_IV, device.getProtocolAddress());
        } catch (Exception error) {
            return fail("GAN 地址或密钥初始化失败", error);
        }
        bindCharacteristics(service);
        if (readCharacteristic == null || writeCharacteristic == null) return fail("GAN 缺少必要的 GATT 特征", null);
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        if (!gatt.setCharacteristicNotification(readCharacteristic, true)) return fail("GAN 无法开启通知", null);
        BluetoothGattDescriptor descriptor = readCharacteristic.getDescriptor(CCCD);
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
        requestQueue.clear();
        pendingMoves.clear();
        gatt = null;
        readCharacteristic = null;
        writeCharacteristic = null;
        variant = null;
        notificationsReady = false;
        writePending = false;
        previousMoveCounter = -1;
        latestMoveCounter = -1;
        requestedHistoryEnd = -1;
        lastDeviceTimestamp = -1;
        lastLocalTimestamp = -1;
    }

    @Override public void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        if (descriptor == null || descriptor.getCharacteristic() == null || readCharacteristic == null) return;
        if (!readCharacteristic.getUuid().equals(descriptor.getCharacteristic().getUuid())) return;
        if (status == BluetoothGatt.GATT_SUCCESS) onNotificationsEnabled();
        else fail("GAN 通知描述符写入失败: " + status, null);
    }

    @Override public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (characteristic == null || writeCharacteristic == null
                || !writeCharacteristic.getUuid().equals(characteristic.getUuid())) return;
        writePending = false;
        if (status != BluetoothGatt.GATT_SUCCESS) fail("GAN 请求写入失败: " + status, null);
        sendNextRequest();
    }

    @Override public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || readCharacteristic == null
                || !readCharacteristic.getUuid().equals(characteristic.getUuid())) return;
        byte[] value = characteristic.getValue();
        if (value == null || value.length < 16) {
            fail("GAN 收到非法数据包长度", null);
            return;
        }
        try {
            parseDecoded(cipher.decode(value));
        } catch (Exception error) {
            fail("GAN 数据解析失败", error);
        }
    }

    @Override public void onMtuChanged(int mtu, int status) { }

    private void parseDecoded(byte[] decoded) {
        String bits = toBits(decoded);
        if (variant == Variant.V2) parseV2(bits);
        else if (variant == Variant.V3) parseV3(bits);
        else parseV4(decoded, bits);
    }

    private void parseV2(String bits) {
        int mode = bitsAt(bits, 0, 4);
        if (mode == 4) {
            synchronize(bitsAt(bits, 4, 8));
        } else if (mode == 9) {
            eventSink.onBatteryChanged(bitsAt(bits, 8, 8));
        } else if (mode == 2) {
            int counter = bitsAt(bits, 4, 8);
            if (previousMoveCounter < 0 || counter == previousMoveCounter) return;
            int difference = Math.min((counter - previousMoveCounter) & 0xff, 7);
            CubeMove[] moves = new CubeMove[7];
            int[] offsets = new int[7];
            for (int i = 0; i < 7; i++) {
                int raw = bitsAt(bits, 12 + i * 5, 5);
                if (raw >= 12) {
                    fail("GAN v2 收到未知转动编号: " + raw, null);
                    return;
                }
                moves[i] = mapAxisPower(raw >> 1, raw & 1);
                offsets[i] = bitsAt(bits, 47 + i * 16, 16);
            }
            for (int i = difference - 1; i >= 0; i--) if (moves[i] != null) eventSink.onMove(moves[i], offsets[i]);
            previousMoveCounter = counter;
        }
    }

    private void parseV3(String bits) {
        if (bitsAt(bits, 0, 8) != 0x55) return;
        int mode = bitsAt(bits, 8, 8);
        int length = bitsAt(bits, 16, 8);
        if (mode == 1) {
            int counter = ((bitsAt(bits, 64, 8) << 8) | bitsAt(bits, 56, 8)) & 0xff;
            long timestamp = readLittleEndianTimestamp(bits, 24);
            addPending(counter, mapAxisMask(bitsAt(bits, 74, 6), bitsAt(bits, 72, 2)), timestamp);
        } else if (mode == 2) {
            handleFaceletCounter(((bitsAt(bits, 32, 8) << 8) | bitsAt(bits, 24, 8)) & 0xff);
        } else if (mode == 6) {
            injectHistory(bits, 32, bitsAt(bits, 24, 8), Math.max(0, (length - 1) * 2));
        } else if (mode == 16) {
            eventSink.onBatteryChanged(bitsAt(bits, 24, 8));
        }
    }

    private void parseV4(byte[] decoded, String bits) {
        int mode = bitsAt(bits, 0, 8);
        int length = bitsAt(bits, 8, 8);
        if (mode == 0x01) {
            for (int[] frame : parseV4MoveFrames(decoded)) {
                addPending(frame[0], CubeMove.Companion.fromStableIndex(frame[1]), Integer.toUnsignedLong(frame[2]));
            }
        } else if (mode == 0xed) {
            handleFaceletCounter(((bitsAt(bits, 24, 8) << 8) | bitsAt(bits, 16, 8)) & 0xff);
        } else if (mode == 0xd1) {
            injectHistory(bits, 24, bitsAt(bits, 16, 8), Math.max(0, (length - 1) * 2));
        } else if (mode == 0xef && 8 + length * 8 + 8 <= bits.length()) {
            eventSink.onBatteryChanged(bitsAt(bits, 8 + length * 8, 8));
        }
    }

    private void synchronize(int counter) {
        if (previousMoveCounter >= 0) return;
        previousMoveCounter = counter;
        latestMoveCounter = counter;
        eventSink.onSyncStateChanged(CubeSyncState.SYNCHRONIZED);
    }

    private void handleFaceletCounter(int counter) {
        if (previousMoveCounter < 0) {
            synchronize(counter);
            return;
        }
        latestMoveCounter = counter;
        int difference = (counter - previousMoveCounter) & 0xff;
        if (difference > 0 && pendingMoves.isEmpty()) requestHistory(counter, difference + 1);
        drainPending();
    }

    private void addPending(int counter, CubeMove move, long deviceTimestamp) {
        if (move == null || previousMoveCounter < 0 || counter == previousMoveCounter) return;
        latestMoveCounter = counter;
        pendingMoves.putIfAbsent(counter, new MoveEvent(move, deviceTimestamp, System.currentTimeMillis()));
        drainPending();
    }

    private void injectHistory(String bits, int offset, int startCounter, int count) {
        int available = Math.max(0, (bits.length() - offset) / 4);
        for (int i = 0; i < Math.min(count, available); i++) {
            int axis = bitsAt(bits, offset + i * 4, 3);
            int power = bitsAt(bits, offset + i * 4 + 3, 1);
            CubeMove move = mapHistoryMove(axis, power);
            int counter = (startCounter - i) & 0xff;
            if (move != null && isCounterInForwardRange(previousMoveCounter, latestMoveCounter, counter)) {
                pendingMoves.putIfAbsent(counter, new MoveEvent(move, null, System.currentTimeMillis()));
            }
        }
        requestedHistoryEnd = -1;
        drainPending();
    }

    private void drainPending() {
        while (previousMoveCounter >= 0) {
            int next = (previousMoveCounter + 1) & 0xff;
            MoveEvent event = pendingMoves.remove(next);
            if (event == null) {
                int difference = latestMoveCounter < 0 ? 0 : (latestMoveCounter - previousMoveCounter) & 0xff;
                if (difference > 0) requestHistory(latestMoveCounter, difference);
                return;
            }
            eventSink.onMove(event.move, calculateDelta(event));
            previousMoveCounter = next;
            if (previousMoveCounter == requestedHistoryEnd) requestedHistoryEnd = -1;
        }
    }

    private int calculateDelta(MoveEvent event) {
        long current = event.deviceTimestamp == null ? -1 : event.deviceTimestamp;
        long delta;
        if (current >= 0 && lastDeviceTimestamp >= 0) delta = current - lastDeviceTimestamp;
        else if (lastLocalTimestamp >= 0) delta = event.localTimestamp - lastLocalTimestamp;
        else delta = 0;
        if (current >= 0) lastDeviceTimestamp = current;
        lastLocalTimestamp = event.localTimestamp;
        return (int) Math.max(0, Math.min(delta, 0xffff));
    }

    private void requestHistory(int endCounter, int count) {
        if (variant == Variant.V2 || count <= 0 || requestedHistoryEnd == endCounter) return;
        int start = endCounter;
        if ((start & 1) == 0) start = (start - 1) & 0xff;
        int requestedCount = (count & 1) == 0 ? count : count + 1;
        requestedCount = Math.min(requestedCount, start + 1);
        requestedHistoryEnd = endCounter;
        byte[] request = variant == Variant.V3 ? new byte[16] : new byte[20];
        if (variant == Variant.V3) {
            request[0] = 0x68; request[1] = 0x03;
        } else {
            request[0] = (byte) 0xd1; request[1] = 0x04;
        }
        request[2] = (byte) start;
        request[4] = (byte) requestedCount;
        enqueue(request);
    }

    private void onNotificationsEnabled() {
        if (notificationsReady) return;
        notificationsReady = true;
        if (variant == Variant.V2) {
            enqueue(simpleRequest(20, 5)); enqueue(simpleRequest(20, 4)); enqueue(simpleRequest(20, 9));
        } else if (variant == Variant.V3) {
            enqueue(v3Request(4)); enqueue(v3Request(1)); enqueue(v3Request(7));
        } else {
            byte[] hardware = new byte[20]; hardware[0] = (byte) 0xdf; hardware[1] = 0x03; enqueue(hardware);
            byte[] facelets = new byte[20]; facelets[0] = (byte) 0xdd; facelets[1] = 0x04; facelets[3] = (byte) 0xed; enqueue(facelets);
            byte[] battery = new byte[20]; battery[0] = (byte) 0xdd; battery[1] = 0x04; battery[3] = (byte) 0xef; enqueue(battery);
        }
    }

    private void enqueue(byte[] request) { requestQueue.offer(request); sendNextRequest(); }

    private void sendNextRequest() {
        if (!notificationsReady || writePending || gatt == null || writeCharacteristic == null || requestQueue.isEmpty()) return;
        try {
            writeCharacteristic.setValue(cipher.encode(requestQueue.poll()));
            writePending = gatt.writeCharacteristic(writeCharacteristic);
            if (!writePending) fail("GAN 请求未能进入写入队列", null);
        } catch (GeneralSecurityException error) {
            fail("GAN 请求加密失败", error);
        }
    }

    private void bindCharacteristics(BluetoothGattService service) {
        if (variant == Variant.V2) {
            readCharacteristic = service.getCharacteristic(V2_READ); writeCharacteristic = service.getCharacteristic(V2_WRITE);
        } else if (variant == Variant.V3) {
            readCharacteristic = service.getCharacteristic(V3_READ); writeCharacteristic = service.getCharacteristic(V3_WRITE);
        } else {
            readCharacteristic = service.getCharacteristic(V4_READ); writeCharacteristic = service.getCharacteristic(V4_WRITE);
        }
    }

    private static Variant resolveVariant(UUID uuid) {
        if (SERVICE_UUID_V2.equals(uuid)) return Variant.V2;
        if (SERVICE_UUID_V3.equals(uuid)) return Variant.V3;
        if (SERVICE_UUID_V4.equals(uuid)) return Variant.V4;
        return null;
    }

    static CubeMove mapAxisPower(int axis, int power) {
        int index = axis * 2 + (power == 0 ? 0 : 1);
        return CubeMove.Companion.fromStableIndex(index);
    }

    private static CubeMove mapAxisMask(int mask, int power) {
        for (int i = 0; i < AXIS_MASKS.length; i++) if (AXIS_MASKS[i] == mask) return mapAxisPower(i, power);
        return null;
    }

    static CubeMove mapHistoryMove(int axis, int power) {
        if (axis < 0 || axis >= 6) return null;
        int mappedAxis = "URFDLB".indexOf("DUBFLR".charAt(axis));
        return mapAxisPower(mappedAxis, power);
    }

    static boolean isCounterInForwardRange(int start, int end, int candidate) {
        if (start < 0 || end < 0) return false;
        int totalDistance = (end - start) & 0xff;
        int candidateDistance = (candidate - start) & 0xff;
        return candidateDistance > 0 && candidateDistance <= totalDistance;
    }

    static int[][] parseV4MoveFrames(byte[] decoded) {
        String bits = toBits(decoded);
        List<int[]> frames = new ArrayList<>();
        for (int offset = 0; offset + 72 <= bits.length(); offset += 72) {
            if (bitsAt(bits, offset, 8) != 0x01) break;
            int counter = ((bitsAt(bits, offset + 56, 8) << 8) | bitsAt(bits, offset + 48, 8)) & 0xff;
            long timestamp = readLittleEndianTimestamp(bits, offset + 16);
            CubeMove move = mapAxisMask(bitsAt(bits, offset + 66, 6), bitsAt(bits, offset + 64, 2));
            if (move == null) break;
            frames.add(new int[]{counter, move.getStableIndex(), (int) timestamp});
        }
        return frames.toArray(new int[0][]);
    }

    private static long readLittleEndianTimestamp(String bits, int offset) {
        long value = 0;
        for (int i = 3; i >= 0; i--) value = (value << 8) | bitsAt(bits, offset + i * 8, 8);
        return value;
    }

    private static byte[] simpleRequest(int length, int opcode) { byte[] value = new byte[length]; value[0] = (byte) opcode; return value; }
    private static byte[] v3Request(int opcode) { byte[] value = new byte[16]; value[0] = 0x68; value[1] = (byte) opcode; return value; }

    private static String toBits(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 8);
        for (byte item : value) result.append(String.format(Locale.US, "%8s", Integer.toBinaryString(item & 0xff)).replace(' ', '0'));
        return result.toString();
    }

    private static int bitsAt(String bits, int start, int length) {
        return Integer.parseInt(bits.substring(start, start + length), 2);
    }

    private boolean fail(String message, Throwable cause) {
        eventSink.onProtocolError(message, cause);
        return false;
    }
}
