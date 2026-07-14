package com.huizhi.rubikey.cube

import android.bluetooth.BluetoothGattService

class CubeProtocolRegistry(providers: List<CubeProtocolProvider>) {
    private val providers = providers.toList()

    fun identify(name: String?): CubeProtocolProvider? = providers.singleOrNull { it.matchesDevice(name) }

    fun resolve(
        services: List<BluetoothGattService>,
        expectedBrand: CubeBrand? = null,
    ): Result<CubeProtocolProvider> {
        val matches = providers.filter { provider ->
            (expectedBrand == null || provider.brand == expectedBrand) && provider.matchesServices(services)
        }
        return when (matches.size) {
            1 -> Result.success(matches.single())
            0 -> Result.failure(IllegalStateException("未找到支持当前 GATT 服务的魔方协议"))
            else -> Result.failure(IllegalStateException("多个魔方协议同时匹配当前 GATT 服务"))
        }
    }

    companion object {
        val default = CubeProtocolRegistry(
            listOf(Moyu32ProtocolProvider(), GanProtocolProvider(), QiyiProtocolProvider()),
        )
    }
}
