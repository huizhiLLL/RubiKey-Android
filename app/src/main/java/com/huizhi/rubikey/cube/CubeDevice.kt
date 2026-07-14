package com.huizhi.rubikey.cube

data class CubeDevice(
    val name: String,
    val address: String,
    val protocolAddress: String = address,
    val brand: CubeBrand,
    val connectionStatus: CubeConnectionStatus = CubeConnectionStatus.DISCOVERED,
)

enum class CubeBrand { MOYU32, GAN, QIYI, UNKNOWN }

enum class CubeConnectionStatus { DISCOVERED, CONNECTING, CONNECTED, DISCONNECTED }
