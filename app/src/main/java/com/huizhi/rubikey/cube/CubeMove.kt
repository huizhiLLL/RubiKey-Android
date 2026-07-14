package com.huizhi.rubikey.cube

/** 三阶魔方的稳定标准转动顺序，持久化和业务层均使用此编号。 */
enum class CubeMove(
    val notation: String,
    val stableIndex: Int,
) {
    U("U", 0), U2("U2", 1), U_PRIME("U'", 2),
    R("R", 3), R2("R2", 4), R_PRIME("R'", 5),
    F("F", 6), F2("F2", 7), F_PRIME("F'", 8),
    D("D", 9), D2("D2", 10), D_PRIME("D'", 11),
    L("L", 12), L2("L2", 13), L_PRIME("L'", 14),
    B("B", 15), B2("B2", 16), B_PRIME("B'", 17);

    companion object {
        fun fromStableIndex(index: Int): CubeMove? = entries.getOrNull(index)

        /** MoYu32 的 0..11 原始转动编号仅包含顺时针和逆时针转动。 */
        fun fromMoyuRaw(rawMove: Int): CubeMove? {
            if (rawMove !in 0..11) return null
            val face = "FBUDLR"[rawMove shr 1]
            val prime = rawMove and 1 == 1
            return entries.firstOrNull { it.notation == "$face${if (prime) "'" else ""}" }
        }
    }
}
