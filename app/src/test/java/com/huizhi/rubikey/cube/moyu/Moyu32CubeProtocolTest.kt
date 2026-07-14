package com.huizhi.rubikey.cube.moyu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Moyu32CubeProtocolTest {
    @Test fun `device information and gyro message types are ignored`() {
        assertTrue(Moyu32CubeProtocol.isIgnoredMessageType(161))
        assertTrue(Moyu32CubeProtocol.isIgnoredMessageType(171))
        assertTrue(Moyu32CubeProtocol.isIgnoredMessageType(172))
        assertFalse(Moyu32CubeProtocol.isIgnoredMessageType(165))
    }
}
