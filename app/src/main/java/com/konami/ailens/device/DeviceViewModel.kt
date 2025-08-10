package com.konami.ailens.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.ble.AiLens
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.command.BLECommand
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceViewModel(app: Application) : AndroidViewModel(app) {
    private val ble = BLEService.getOrCreate(app)

    private val _navigationEvent = Channel<AiLens>(capacity = Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    val uiItems: StateFlow<List<DeviceAdapter.BLEListItem>> =
        combine(ble.lastDevice, ble.devices) { last, devices ->
            val items = mutableListOf<DeviceAdapter.BLEListItem>()
            items += DeviceAdapter.BLEListItem.Header("My Glasses")
            last?.let { items += DeviceAdapter.BLEListItem.Device(it) }
            items += DeviceAdapter.BLEListItem.Header("Available Glasses")
            val lastMac = last?.device?.address
            items += devices.filter { it.device.address != lastMac }.map { DeviceAdapter.BLEListItem.Device(it) }
            items
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun actionItem(aiLens: AiLens) {
        val last = ble.lastDevice.value
        when {
            last?.device?.address == aiLens.device.address -> {
                when (aiLens.state) {
                    AiLens.State.CONNECTED -> {
                        viewModelScope.launch {
                            _navigationEvent.send(last)
                        }
                    }
                    AiLens.State.DISCONNECTED -> ble.retrieve()
                    else -> Unit
                }
            }
            aiLens.state == AiLens.State.AVAILABLE -> ble.connect(aiLens.device.address)
            else -> Unit
        }
    }

    // 透過 Service 橋接指令
    fun send(address: String, cmd: BLECommand) = ble.addCommand(address, cmd)
    fun sendRaw(address: String, bytes: ByteArray) = ble.sendRaw(address, bytes)
    fun stopCommands(address: String) = ble.stopCommands(address)
    fun enableStream(address: String) = ble.setStreamNotifyOn(address)
}