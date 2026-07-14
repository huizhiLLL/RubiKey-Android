package com.huizhi.rubikey.cube

/** Moyu32 可报告的单步转动，持久化和业务层均使用此编号。 */
enum class CubeMove(
    val notation: String,
    val stableIndex: Int,
) {
    U("U", 0), U_PRIME("U'", 1),
    R("R", 2), R_PRIME("R'", 3),
    F("F", 4), F_PRIME("F'", 5),
    D("D", 6), D_PRIME("D'", 7),
    L("L", 8), L_PRIME("L'", 9),
    B("B", 10), B_PRIME("B'", 11);

    companion object {
        fun fromStableIndex(index: Int): CubeMove? = entries.getOrNull(index)

        /** Moyu32 的 0..11 原始转动编号仅包含六个面的正反单步转动。 */
        fun fromMoyuRaw(rawMove: Int): CubeMove? = when (rawMove) {
            0 -> F
            1 -> F_PRIME
            2 -> B
            3 -> B_PRIME
            4 -> U
            5 -> U_PRIME
            6 -> D
            7 -> D_PRIME
            8 -> L
            9 -> L_PRIME
            10 -> R
            11 -> R_PRIME
            else -> null
        }
    }
}
