package com.huizhi.rubikey.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureActionExecutorTest {
    @Test fun `full queue drops oldest waiting action and preserves current action`() {
        val started = mutableListOf<Int>()
        val callbacks = mutableListOf<() -> Unit>()
        var dropped = 0
        val executor = GestureActionExecutor(
            dispatcher = GestureDispatcher { request, completion ->
                started += (request as GestureRequest.Tap).x.toInt()
                callbacks += completion
                true
            },
            onDrop = { dropped++ },
        )
        repeat(6) { executor.submit(GestureRequest.Tap((it + 1).toFloat(), 0f)) }

        assertEquals(listOf(1), started)
        assertEquals(1, dropped)
        callbacks.removeAt(0).invoke()
        assertEquals(listOf(1, 3), started)
    }
}
