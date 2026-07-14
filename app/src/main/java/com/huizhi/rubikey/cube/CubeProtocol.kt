package com.huizhi.rubikey.cube

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService

interface CubeProtocol {
    fun start(gatt: BluetoothGatt, service: BluetoothGattService, device: CubeDevice): Boolean
    fun clear()
    fun onDescriptorWrite(descriptor: BluetoothGattDescriptor, status: Int)
    fun onCharacteristicWrite(characteristic: BluetoothGattCharacteristic, status: Int)
    fun onCharacteristicChanged(characteristic: BluetoothGattCharacteristic)
    fun onMtuChanged(mtu: Int, status: Int) = Unit
}
