package com.huizhi.rubikey.cube

interface CubeEventSink {
    fun onMove(move: CubeMove, elapsedMs: Int)
    fun onBatteryChanged(percent: Int)
    fun onSyncStateChanged(state: CubeSyncState)
    fun onProtocolError(message: String, cause: Throwable? = null)
}

enum class CubeSyncState { WAITING_FOR_INITIAL_STATE, SYNCHRONIZED, INVALID_STATE }
