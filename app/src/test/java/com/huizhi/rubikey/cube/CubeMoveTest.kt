package com.huizhi.rubikey.cube

import android.bluetooth.BluetoothGattService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeMoveTest {
    @Test fun `stable indexes cover all 18 moves`() {
        assertEquals(18, CubeMove.entries.size)
        CubeMove.entries.forEach { move -> assertEquals(move, CubeMove.fromStableIndex(move.stableIndex)) }
        assertNull(CubeMove.fromStableIndex(-1))
        assertNull(CubeMove.fromStableIndex(18))
    }

    @Test fun `moyu raw indexes convert to standard moves`() {
        assertEquals(CubeMove.F, CubeMove.fromMoyuRaw(0))
        assertEquals(CubeMove.F_PRIME, CubeMove.fromMoyuRaw(1))
        assertEquals(CubeMove.U, CubeMove.fromMoyuRaw(4))
        assertEquals(CubeMove.R_PRIME, CubeMove.fromMoyuRaw(11))
        assertNull(CubeMove.fromMoyuRaw(12))
    }

    @Test fun `registry reports no match and duplicate matches`() {
        val noMatch = CubeProtocolRegistry(emptyList()).resolve(emptyList())
        assertTrue(noMatch.isFailure)
        val registry = CubeProtocolRegistry(listOf(FakeProvider(true), FakeProvider(true)))
        assertTrue(registry.resolve(emptyList()).isFailure)
        assertFalse(CubeProtocolRegistry(listOf(FakeProvider(false))).resolve(emptyList()).isSuccess)
    }

    private class FakeProvider(private val serviceMatch: Boolean) : CubeProtocolProvider {
        override val brand = CubeBrand.UNKNOWN
        override fun matchesDevice(name: String?): Boolean = false
        override fun matchesServices(services: List<BluetoothGattService>): Boolean = serviceMatch
        override fun findService(services: List<BluetoothGattService>): BluetoothGattService? = null
        override fun create(eventSink: CubeEventSink): CubeProtocol = error("not used")
    }
}
