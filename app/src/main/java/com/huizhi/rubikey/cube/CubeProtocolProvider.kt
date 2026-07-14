package com.huizhi.rubikey.cube

import android.bluetooth.BluetoothGattService

interface CubeProtocolProvider {
    val brand: CubeBrand
    fun matchesDevice(name: String?): Boolean
    fun matchesServices(services: List<BluetoothGattService>): Boolean
    fun findService(services: List<BluetoothGattService>): BluetoothGattService?
    fun create(eventSink: CubeEventSink): CubeProtocol
}
