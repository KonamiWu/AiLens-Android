package com.konami.ailens.ble.ota

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import android.util.Log
import com.konami.ailens.ble.Glasses
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class AiRing(private val context: Context, var device: BluetoothDevice, private val retrieveToken: ByteArray? = null): BluetoothGattCallback()  {
    private var gatt: BluetoothGatt? = null

    fun connect() {
//        _state.value = Glasses.State.CONNECTING
        gatt = device.connectGatt(context, true, this, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
            Log.e("TAG", "connected")
            if (retrieveToken == null) {
//                _state.value = Glasses.State.CONNECTING
            }
            gatt?.requestMtu(512)
            scope.launch {
                delay(500)
                Log.e("TAG", "gatt = ${gatt}")
                gatt?.discoverServices()
            }
        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
//            _state.value = Glasses.State.DISCONNECTED
            // Now it's safe to close and cleanup
            cleanup()
        } else if (status != BluetoothGatt.GATT_SUCCESS) {
//            _state.value = Glasses.State.DISCONNECTED
            cleanup()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.e("TAG", "onServicesDiscovered")
        gatt.services.forEach {
            Log.e("TAG", "service = ${it}")
            it.characteristics.forEach { characteristic ->
                Log.e("TAG", "characteristic = ${characteristic}")
            }
        }
    }

    private fun cleanup() {

    }
}