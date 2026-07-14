package com.huizhi.rubikey.mapping

import com.huizhi.rubikey.cube.CubeMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionMappingTest {
    @Test fun `empty mapping covers every Moyu32 move`() {
        val mapping = ActionMapping.empty()
        assertEquals(12, mapping.entries().size)
        assertEquals(CubeAction.None, mapping[CubeMove.U])
    }

    @Test fun `mapping rejects out of range coordinates and duration`() {
        assertThrows(IllegalArgumentException::class.java) {
            ActionMapping.empty().with(CubeMove.U, CubeAction.Tap(-0.01f, 0.5f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActionMapping.empty().with(CubeMove.U, CubeAction.Swipe(0f, 0f, 1f, 1f, 39))
        }
    }
}
