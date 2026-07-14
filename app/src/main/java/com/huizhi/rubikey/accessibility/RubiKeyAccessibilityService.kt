package com.huizhi.rubikey.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.huizhi.rubikey.mapping.CubeAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RubiKeyAccessibilityService : AccessibilityService() {
    private lateinit var executor: GestureActionExecutor

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _enabled.value = true
        _droppedActions.value = 0
        _lastError.value = null
        executor = GestureActionExecutor(
            dispatcher = GestureDispatcher { request, completion -> dispatch(request, completion) },
            onDrop = { _droppedActions.value++ },
        )
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        clearInstance()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearInstance()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun dispatch(request: GestureRequest, completion: () -> Unit): Boolean {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        if (metrics.widthPixels > metrics.heightPixels) {
            _lastError.value = "当前仅支持竖屏显示"
            return false
        }
        val path = Path()
        val gesture = when (request) {
            is GestureRequest.Tap -> {
                path.moveTo(request.x * metrics.widthPixels, request.y * metrics.heightPixels)
                GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, CubeAction.TAP_DURATION_MS)).build()
            }
            is GestureRequest.Swipe -> {
                path.moveTo(request.startX * metrics.widthPixels, request.startY * metrics.heightPixels)
                path.lineTo(request.endX * metrics.widthPixels, request.endY * metrics.heightPixels)
                GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, request.durationMs)).build()
            }
        }
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { completion() }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                _lastError.value = "系统取消了手势"
                completion()
            }
        }, null).also { accepted -> if (!accepted) _lastError.value = "系统拒绝了手势" }
    }

    private fun submit(action: CubeAction) {
        val request = when (action) {
            CubeAction.None -> return
            is CubeAction.Tap -> GestureRequest.Tap(action.x, action.y)
            is CubeAction.Swipe -> GestureRequest.Swipe(action.startX, action.startY, action.endX, action.endY, action.durationMs)
        }
        executor.submit(request)
    }

    private fun clear() { if (::executor.isInitialized) executor.clear() }

    private fun clearInstance() {
        clear()
        if (instance === this) instance = null
        _enabled.value = false
    }

    companion object {
        private var instance: RubiKeyAccessibilityService? = null
        private val _enabled = MutableStateFlow(false)
        private val _droppedActions = MutableStateFlow(0L)
        private val _lastError = MutableStateFlow<String?>(null)
        val enabled = _enabled.asStateFlow()
        val droppedActions = _droppedActions.asStateFlow()
        val lastError = _lastError.asStateFlow()

        fun submitAction(action: CubeAction) {
            val service = instance
            if (service == null) _lastError.value = "辅助功能服务未启用" else service.submit(action)
        }

        fun clearPendingActions() { instance?.clear() }
    }
}
