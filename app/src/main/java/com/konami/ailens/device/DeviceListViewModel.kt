package com.konami.ailens.device

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.DeviceSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DeviceListViewModel: ViewModel() {
    private val _updateFlow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 2)
    val updateFlow = _updateFlow.asSharedFlow()

    private val _deviceConnectStateFlow = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1)
    val deviceConnectedFlow = _deviceConnectStateFlow.asSharedFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            BLEService.instance.updateFlow.collect {
                _updateFlow.tryEmit(Unit)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        job = null
        Log.d("DeviceListViewModel", "onCleared - job cancelled")
    }

    fun stopSearch() {
        BLEService.instance.stopScan()
    }

    fun getItems(): List<DeviceSession> {
        return BLEService.instance.sessions.values.toList()
    }

    fun connect(deviceSession: DeviceSession) {
        job?.cancel()
        job = viewModelScope.launch {
            deviceSession.state.collect {
                if (it == DeviceSession.State.CONNECTED)
                    _deviceConnectStateFlow.emit(true)
                else if(it == DeviceSession.State.DISCONNECTED) {
                    _deviceConnectStateFlow.emit(false)
                }
            }
        }
        deviceSession.connect()
    }
}