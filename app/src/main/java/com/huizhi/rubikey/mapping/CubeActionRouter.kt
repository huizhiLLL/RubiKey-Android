package com.huizhi.rubikey.mapping

import com.huizhi.rubikey.cube.CubeMove

class CubeActionRouter(
    private val mappingProvider: () -> ActionMapping,
    private val submitAction: (CubeAction) -> Unit,
) {
    fun route(move: CubeMove) {
        val action = mappingProvider()[move]
        if (action !is CubeAction.None && CubeAction.isValid(action)) submitAction(action)
    }
}
