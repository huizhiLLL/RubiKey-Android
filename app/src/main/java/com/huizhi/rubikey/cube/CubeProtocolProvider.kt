package com.huizhi.rubikey.cube

import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanResult

interface CubeProtocolProvider {
    val brand: CubeBrand
    fun matchesDevice(name: String?): Boolean
    fun matchesServices(services: List<BluetoothGattService>): Boolean
    fun findService(services: List<BluetoothGattService>): BluetoothGattService?
    fun resolveProtocolAddress(result: ScanResult, connectionAddress: String): String = connectionAddress
    fun create(eventSink: CubeEventSink): CubeProtocol
}
