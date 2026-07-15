package com.huizhi.rubikey.mapping

import android.content.Context
import androidx.core.content.edit
import com.huizhi.rubikey.cube.CubeMove
import org.json.JSONArray
import org.json.JSONObject

class ActionMappingRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): LoadResult {
        val raw = preferences.getString(KEY_MAPPING, null) ?: return LoadResult(ActionMapping.empty())
        return runCatching { decode(raw) }
            .fold(
                onSuccess = { LoadResult(it) },
                onFailure = { LoadResult(ActionMapping.empty(), "动作映射损坏，已使用空映射") },
            )
    }

    fun save(mapping: ActionMapping) {
        preferences.edit { putString(KEY_MAPPING, encode(mapping)) }
    }

    private fun encode(mapping: ActionMapping): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("actions", JSONArray().apply {
            mapping.entries().forEach { (move, action) ->
                put(JSONObject().apply {
                    put("move", move.stableIndex)
                    when (action) {
                        CubeAction.None -> put("type", "none")
                        is CubeAction.Tap -> {
                            put("type", "tap"); put("x", action.x.toDouble()); put("y", action.y.toDouble())
                        }
                        is CubeAction.Swipe -> {
                            put("type", "swipe")
                            put("startX", action.startX.toDouble()); put("startY", action.startY.toDouble())
                            put("endX", action.endX.toDouble()); put("endY", action.endY.toDouble())
                            put("durationMs", action.durationMs)
                        }
                    }
                })
            }
        })
    }.toString()

    private fun decode(raw: String): ActionMapping {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "不支持的映射版本" }
        val records = root.getJSONArray("actions")
        require(records.length() == CubeMove.entries.size) { "动作数量不完整" }
        val actions = mutableMapOf<CubeMove, CubeAction>()
        for (index in 0 until records.length()) {
            val item = records.getJSONObject(index)
            val move = CubeMove.fromStableIndex(item.getInt("move")) ?: error("未知转动")
            check(actions[move] == null) { "重复转动" }
            actions[move] = when (item.getString("type")) {
                "none" -> CubeAction.None
                "tap" -> CubeAction.Tap(item.requiredCoordinate("x"), item.requiredCoordinate("y"))
                "swipe" -> CubeAction.Swipe(
                    item.requiredCoordinate("startX"), item.requiredCoordinate("startY"),
                    item.requiredCoordinate("endX"), item.requiredCoordinate("endY"), item.getLong("durationMs"),
                )
                else -> error("未知动作类型")
            }
        }
        return ActionMapping.from(actions)
    }

    private fun JSONObject.requiredCoordinate(key: String): Float {
        require(has(key)) { "缺少坐标" }
        val value = getDouble(key).toFloat()
        require(value.isFinite() && value in 0f..1f) { "坐标越界" }
        return value
    }

    data class LoadResult(val mapping: ActionMapping, val errorMessage: String? = null)

    private companion object {
        const val PREFERENCES_NAME = "action_mapping"
        const val KEY_MAPPING = "schema_v1"
        const val SCHEMA_VERSION = 1
    }
}
