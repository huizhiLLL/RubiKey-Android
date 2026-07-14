package com.huizhi.rubikey.cube

import android.bluetooth.BluetoothGattService
import com.huizhi.rubikey.cube.gan.GanCubeProtocol

class GanProtocolProvider : CubeProtocolProvider {
    override val brand = CubeBrand.GAN

    override fun matchesDevice(name: String?): Boolean = GanCubeProtocol.matchesDeviceName(name)

    override fun matchesServices(services: List<BluetoothGattService>): Boolean = findService(services) != null

    override fun findService(services: List<BluetoothGattService>): BluetoothGattService? =
        services.firstOrNull { it.uuid in GanCubeProtocol.SERVICE_UUIDS }

    override fun create(eventSink: CubeEventSink): CubeProtocol = GanCubeProtocol(eventSink)
}
