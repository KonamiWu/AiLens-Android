package com.konami.ailens.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.DeviceSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceViewModel(app: Application) : AndroidViewModel(app) {
    private val service = BLEService.getOrCreate(app)

    private val _navigationEvent = Channel<DeviceSession>(capacity = Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private val _updateFlow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val updateFlow = _updateFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            service.updateFlow.collect {
                _updateFlow.tryEmit(Unit)
            }
        }
    }
    fun getItems(): List<DeviceAdapter.BLEListItem> {
        val last = service.connectedSession.value
        val sessions = service.sessions
        val items = mutableListOf<DeviceAdapter.BLEListItem>()
        items += DeviceAdapter.BLEListItem.Header("My Glasses")
        last?.let { items += DeviceAdapter.BLEListItem.Device(it) }
        items += DeviceAdapter.BLEListItem.Header("Available Glasses")
        items += sessions.values.toMutableList().map { DeviceAdapter.BLEListItem.Device(it) }

        return items
    }

    fun onClick(session: DeviceSession) {
        val last = service.connectedSession.value
        if (last != null && last.state.value == DeviceSession.State.CONNECTED && last.device.address == session.device.address) {
            viewModelScope.launch {
                _navigationEvent.send(last)
            }
        } else {
            session.connect()
        }
    }

    fun longPressItem(session: DeviceSession) {
        val last = service.connectedSession.value
        when {
            last?.device?.address == session.device.address -> {
                when (session.state.value) {
                    DeviceSession.State.CONNECTED -> {
                        session.disconnect()
                    }
                    else -> Unit
                }
            }
        }
    }
}