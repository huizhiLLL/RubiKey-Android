package com.huizhi.rubikey.mapping

sealed interface CubeAction {
    data object None : CubeAction
    data class Tap(val x: Float, val y: Float) : CubeAction
    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val durationMs: Long = DEFAULT_SWIPE_DURATION_MS,
    ) : CubeAction

    companion object {
        const val TAP_DURATION_MS = 40L
        const val DEFAULT_SWIPE_DURATION_MS = 100L
        const val MIN_SWIPE_DURATION_MS = 40L
        const val MAX_SWIPE_DURATION_MS = 500L

        fun isValid(action: CubeAction): Boolean = when (action) {
            None -> true
            is Tap -> action.x.isNormalized() && action.y.isNormalized()
            is Swipe -> action.startX.isNormalized() && action.startY.isNormalized() &&
                action.endX.isNormalized() && action.endY.isNormalized() &&
                action.durationMs in MIN_SWIPE_DURATION_MS..MAX_SWIPE_DURATION_MS
        }
    }
}

private fun Float.isNormalized(): Boolean = isFinite() && this in 0f..1f
