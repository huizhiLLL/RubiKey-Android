package com.huizhi.rubikey.mapping

import com.huizhi.rubikey.cube.CubeMove

class ActionMapping private constructor(private val actions: Map<CubeMove, CubeAction>) {
    operator fun get(move: CubeMove): CubeAction = actions.getValue(move)
    fun entries(): List<Pair<CubeMove, CubeAction>> = CubeMove.entries.map { it to actions.getValue(it) }

    fun with(move: CubeMove, action: CubeAction): ActionMapping {
        require(CubeAction.isValid(action)) { "动作参数无效" }
        return ActionMapping(actions + (move to action))
    }

    companion object {
        fun empty(): ActionMapping = ActionMapping(CubeMove.entries.associateWith { CubeAction.None })

        fun from(actions: Map<CubeMove, CubeAction>): ActionMapping {
            require(actions.keys.containsAll(CubeMove.entries)) { "映射缺少标准转动" }
            require(actions.values.all(CubeAction::isValid)) { "映射包含无效动作" }
            return ActionMapping(CubeMove.entries.associateWith { actions.getValue(it) })
        }
    }
}
