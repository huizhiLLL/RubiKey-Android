package com.huizhi.rubikey.mapping

import com.huizhi.rubikey.cube.CubeMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActionMappingTest {
    @Test fun `empty mapping covers every standard move`() {
        val mapping = ActionMapping.empty()
        assertEquals(CubeMove.entries.size, mapping.entries().size)
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
