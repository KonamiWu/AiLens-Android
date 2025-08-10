package com.konami.ailens.ble

import android.bluetooth.BluetoothDevice

data class AiLens(
    val device: BluetoothDevice,
    val state: State = State.AVAILABLE
) {
    enum class State(val value: String) {
        DISCONNECTED("Disconnected"),
        CONNECTING("Connecting"),
        CONNECTED("Connected"),
        PAIRING("Pairing"),
        AVAILABLE("Available")
    }
}