package com.huizhi.rubikey.accessibility

import java.util.ArrayDeque

/** 单线程手势队列。当前手势不会被新输入取消，队满时淘汰最旧等待项。 */
class GestureActionExecutor(
    private val dispatcher: GestureDispatcher,
    private val onDrop: () -> Unit = {},
) {
    private val pending = ArrayDeque<GestureRequest>()
    private var executing = false

    @Synchronized
    fun submit(request: GestureRequest) {
        if (executing) {
            if (pending.size == MAX_PENDING) { pending.removeFirst(); onDrop() }
            pending.addLast(request)
            return
        }
        dispatch(request)
    }

    @Synchronized
    fun clear() {
        pending.clear()
    }

    @Synchronized
    private fun dispatch(request: GestureRequest) {
        executing = true
        if (!dispatcher.dispatch(request) { completeCurrent() }) completeCurrent()
    }

    @Synchronized
    private fun completeCurrent() {
        executing = false
        if (pending.isNotEmpty()) dispatch(pending.removeFirst())
    }

    companion object { const val MAX_PENDING = 4 }
}

fun interface GestureDispatcher {
    fun dispatch(request: GestureRequest, completion: () -> Unit): Boolean
}

sealed interface GestureRequest {
    data class Tap(val x: Float, val y: Float) : GestureRequest
    data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long) : GestureRequest
}
