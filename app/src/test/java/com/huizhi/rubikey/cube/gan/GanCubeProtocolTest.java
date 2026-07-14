package com.huizhi.rubikey.cube.gan;

import com.huizhi.rubikey.cube.CubeMove;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GanCubeProtocolTest {
    @Test public void deviceNamesIncludeGanCubesButExcludeRobot() {
        assertTrue(GanCubeProtocol.matchesDeviceName("GAN12ui"));
        assertTrue(GanCubeProtocol.matchesDeviceName("MG356"));
        assertTrue(GanCubeProtocol.matchesDeviceName("AiCube-01"));
        assertFalse(GanCubeProtocol.matchesDeviceName("GANBOT-1234"));
        assertFalse(GanCubeProtocol.matchesDeviceName("QY-QYSC-ABCD"));
    }

    @Test public void axisAndHistoryMappingsUseStandardCubeMoves() {
        assertEquals(CubeMove.U, GanCubeProtocol.mapAxisPower(0, 0));
        assertEquals(CubeMove.R_PRIME, GanCubeProtocol.mapAxisPower(1, 1));
        assertEquals(CubeMove.B_PRIME, GanCubeProtocol.mapAxisPower(5, 2));
        assertEquals(CubeMove.D, GanCubeProtocol.mapHistoryMove(0, 0));
        assertEquals(CubeMove.R_PRIME, GanCubeProtocol.mapHistoryMove(5, 1));
    }

    @Test public void v4MoveNotificationParsesEvery72BitFrame() {
        byte[] packet = new byte[18];
        putV4Frame(packet, 0, 41, 1000, 0, 2);
        putV4Frame(packet, 9, 42, 1040, 1, 32);

        int[][] frames = GanCubeProtocol.parseV4MoveFrames(packet);

        assertEquals(2, frames.length);
        assertArrayEquals(new int[]{41, CubeMove.U.getStableIndex(), 1000}, frames[0]);
        assertArrayEquals(new int[]{42, CubeMove.R_PRIME.getStableIndex(), 1040}, frames[1]);
    }

    @Test public void cipherRoundTripPreservesPayload() throws Exception {
        GanCubeCipher cipher = new GanCubeCipher();
        byte[] key = new byte[16];
        byte[] iv = new byte[16];
        Arrays.fill(key, (byte) 7);
        Arrays.fill(iv, (byte) 11);
        cipher.init(key, iv, "01:23:45:67:89:AB");
        byte[] payload = new byte[20];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;

        assertArrayEquals(payload, cipher.decode(cipher.encode(payload)));
    }

    private static void putV4Frame(byte[] target, int offset, int counter, int timestamp, int power, int axisMask) {
        target[offset] = 0x01;
        target[offset + 2] = (byte) timestamp;
        target[offset + 3] = (byte) (timestamp >> 8);
        target[offset + 4] = (byte) (timestamp >> 16);
        target[offset + 5] = (byte) (timestamp >> 24);
        target[offset + 6] = (byte) counter;
        target[offset + 7] = 0;
        target[offset + 8] = (byte) ((power << 6) | axisMask);
    }
}
