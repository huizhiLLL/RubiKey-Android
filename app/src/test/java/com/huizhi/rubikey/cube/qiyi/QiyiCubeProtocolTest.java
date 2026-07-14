package com.huizhi.rubikey.cube.qiyi;

import com.huizhi.rubikey.cube.CubeMove;
import com.huizhi.rubikey.cube.QiyiProtocolProvider;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QiyiCubeProtocolTest {
    @Test public void deviceNamesAndManufacturerAddressMatchDctimerRules() {
        assertTrue(QiyiCubeProtocol.matchesDeviceName("QY-QYSC-12AB"));
        assertTrue(QiyiCubeProtocol.matchesDeviceName("XMD-TornadoV4-I-12AB"));
        assertFalse(QiyiCubeProtocol.matchesDeviceName("QY-Timer"));
        assertEquals("06:05:04:03:02:01",
                QiyiProtocolProvider.protocolAddressFromManufacturerData(new byte[]{1, 2, 3, 4, 5, 6}));
        assertNull(QiyiProtocolProvider.protocolAddressFromManufacturerData(new byte[]{1, 2, 3}));
    }

    @Test public void rawMovesConvertToStandardMoves() {
        assertEquals(CubeMove.L_PRIME, QiyiCubeProtocol.convertMove(1));
        assertEquals(CubeMove.L, QiyiCubeProtocol.convertMove(2));
        assertEquals(CubeMove.U_PRIME, QiyiCubeProtocol.convertMove(7));
        assertEquals(CubeMove.U, QiyiCubeProtocol.convertMove(8));
        assertNull(QiyiCubeProtocol.convertMove(13));
    }

    @Test public void historySlotsAreSortedDeduplicatedAndFutureMovesDeferred() {
        byte[] message = newStateMessage(96, 9, 300);
        putHistory(message, 0, 5, 280);
        putHistory(message, 2, 3, 220);
        putHistory(message, 10, 2, 340);
        putHistory(message, 3, 9, 300);

        QiyiCubeProtocol.MoveSample[] moves = QiyiCubeProtocol.collectStateChangeMoves(message, 200, 300);

        assertEquals(3, moves.length);
        assertMove(moves[0], 3, 220);
        assertMove(moves[1], 5, 280);
        assertMove(moves[2], 9, 300);
    }

    @Test public void crcAppendedInLittleEndianValidatesToZero() {
        byte[] message = {(byte) 0xfe, 0x06, 0x02, 0x01, 0, 0};
        int crc = QiyiCubeProtocol.crc16Modbus(message, 4);
        message[4] = (byte) crc;
        message[5] = (byte) (crc >> 8);
        assertEquals(0, QiyiCubeProtocol.crc16Modbus(message, message.length));
    }

    private static byte[] newStateMessage(int length, int move, long timestamp) {
        byte[] message = new byte[length];
        putTimestamp(message, 3, timestamp);
        message[34] = (byte) move;
        for (int slot = 0; slot < 11; slot++) {
            int offset = 36 + slot * 5;
            for (int i = 0; i < 5; i++) message[offset + i] = (byte) 0xff;
        }
        return message;
    }

    private static void putHistory(byte[] message, int slot, int move, long timestamp) {
        int offset = 36 + slot * 5;
        putTimestamp(message, offset, timestamp);
        message[offset + 4] = (byte) move;
    }

    private static void putTimestamp(byte[] target, int offset, long timestamp) {
        target[offset] = (byte) (timestamp >> 24);
        target[offset + 1] = (byte) (timestamp >> 16);
        target[offset + 2] = (byte) (timestamp >> 8);
        target[offset + 3] = (byte) timestamp;
    }

    private static void assertMove(QiyiCubeProtocol.MoveSample sample, int move, long timestamp) {
        assertEquals(move, sample.move);
        assertEquals(timestamp, sample.timestamp);
    }
}
