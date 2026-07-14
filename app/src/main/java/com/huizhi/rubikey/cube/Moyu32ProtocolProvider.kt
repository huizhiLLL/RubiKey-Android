package com.huizhi.rubikey.cube

import android.bluetooth.BluetoothGattService
import com.huizhi.rubikey.cube.moyu.Moyu32CubeProtocol

class Moyu32ProtocolProvider : CubeProtocolProvider {
    override val brand = CubeBrand.MOYU32

    override fun matchesDevice(name: String?): Boolean =
        name?.trim()?.startsWith("WCU_MY3", ignoreCase = true) == true

    override fun matchesServices(services: List<BluetoothGattService>): Boolean = services.any { service ->
        service.uuid == Moyu32CubeProtocol.SERVICE_UUID ||
            (service.getCharacteristic(Moyu32CubeProtocol.READ_UUID) != null &&
                service.getCharacteristic(Moyu32CubeProtocol.WRITE_UUID) != null)
    }

    override fun findService(services: List<BluetoothGattService>): BluetoothGattService? = services.firstOrNull { service ->
        service.uuid == Moyu32CubeProtocol.SERVICE_UUID ||
            (service.getCharacteristic(Moyu32CubeProtocol.READ_UUID) != null &&
                service.getCharacteristic(Moyu32CubeProtocol.WRITE_UUID) != null)
    }

    override fun create(eventSink: CubeEventSink): CubeProtocol = Moyu32CubeProtocol(eventSink)
}
