package com.huizhi.rubikey.cube

/** 仅维护协议同步所需的计数和最近转动，不承载计时或复原业务。 */
class CubeStateTracker {
    var moveCounter: Int? = null
        private set
    var lastMove: CubeMove? = null
        private set
    var receivedMoveCount: Long = 0
        private set

    fun synchronize(counter: Int) {
        require(counter in 0..255)
        moveCounter = counter
    }

    fun apply(move: CubeMove, counter: Int) {
        require(counter in 0..255)
        lastMove = move
        moveCounter = counter
        receivedMoveCount++
    }

    fun clear() {
        moveCounter = null
        lastMove = null
        receivedMoveCount = 0
    }
}
