package com.konami.ailens.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import com.konami.ailens.ble.command.BLECommand
import com.konami.ailens.orchestrator.capability.DeviceEventCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface Glasses {
    sealed class State(val description: String) {
        object AVAILABLE : State("Available")
        object CONNECTING : State("Connecting")
        object CONNECTED : State("Connected")
        object PAIRING : State("Pairing")
        object DISCONNECTED : State("Disconnected")
    }

    var deviceEventHandler: DeviceEventCapability?
    var device: BluetoothDevice
    var onStreamData: ((ByteArray) -> Unit)?
    val state: StateFlow<State>
    val batteryFlow: StateFlow<Pair<Int, Boolean>>
    val mtu: Int

    fun connect()
    fun disconnect()
    fun add(command: BLECommand<*>)
    fun add(action: () -> Unit)
    fun sendRaw(bytes: ByteArray)
}