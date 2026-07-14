package com.huizhi.rubikey.cube

import android.bluetooth.BluetoothGattService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeMoveTest {
    @Test fun `stable indexes cover all 12 Moyu32 moves`() {
        assertEquals(12, CubeMove.entries.size)
        CubeMove.entries.forEach { move -> assertEquals(move, CubeMove.fromStableIndex(move.stableIndex)) }
        assertNull(CubeMove.fromStableIndex(-1))
        assertNull(CubeMove.fromStableIndex(12))
    }

    @Test fun `moyu raw indexes convert to standard moves`() {
        assertEquals(CubeMove.F, CubeMove.fromMoyuRaw(0))
        assertEquals(CubeMove.F_PRIME, CubeMove.fromMoyuRaw(1))
        assertEquals(CubeMove.B, CubeMove.fromMoyuRaw(2))
        assertEquals(CubeMove.B_PRIME, CubeMove.fromMoyuRaw(3))
        assertEquals(CubeMove.U, CubeMove.fromMoyuRaw(4))
        assertEquals(CubeMove.U_PRIME, CubeMove.fromMoyuRaw(5))
        assertEquals(CubeMove.D, CubeMove.fromMoyuRaw(6))
        assertEquals(CubeMove.D_PRIME, CubeMove.fromMoyuRaw(7))
        assertEquals(CubeMove.L, CubeMove.fromMoyuRaw(8))
        assertEquals(CubeMove.L_PRIME, CubeMove.fromMoyuRaw(9))
        assertEquals(CubeMove.R, CubeMove.fromMoyuRaw(10))
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

    @Test fun `registry uses scanned brand to disambiguate overlapping services`() {
        val registry = CubeProtocolRegistry(listOf(FakeProvider(true, CubeBrand.GAN), FakeProvider(true, CubeBrand.QIYI)))

        assertEquals(CubeBrand.GAN, registry.resolve(emptyList(), CubeBrand.GAN).getOrThrow().brand)
        assertEquals(CubeBrand.QIYI, registry.resolve(emptyList(), CubeBrand.QIYI).getOrThrow().brand)
    }

    private class FakeProvider(
        private val serviceMatch: Boolean,
        override val brand: CubeBrand = CubeBrand.UNKNOWN,
    ) : CubeProtocolProvider {
        override fun matchesDevice(name: String?): Boolean = false
        override fun matchesServices(services: List<BluetoothGattService>): Boolean = serviceMatch
        override fun findService(services: List<BluetoothGattService>): BluetoothGattService? = null
        override fun create(eventSink: CubeEventSink): CubeProtocol = error("not used")
    }
}
