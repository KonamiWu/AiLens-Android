package com.konami.ailens

import AiLens
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DeviceViewModel : ViewModel() {
    private val _availableGlasses = MutableStateFlow<List<AiLens>>(emptyList())
    val availableGlasses: StateFlow<List<AiLens>> = _availableGlasses

    private val _lastGlasses = MutableStateFlow<AiLens?>(null)
    val lastGlasses: StateFlow<AiLens?> = _lastGlasses

    init {
        viewModelScope.launch {
            while (!BLEService.isInitialized()) {
                delay(100)
            }
            BLEService.instance.availableDevices.collect {
                _availableGlasses.value = it
            }
        }

        viewModelScope.launch {
            while (!BLEService.isInitialized()) {
                delay(100)
            }
            BLEService.instance.lastDevice.collect {
                _lastGlasses.value = it
            }
        }
    }

    fun actionItem(glasses: AiLens) {
        if (glasses.device.address == _lastGlasses.value?.device?.address) {
            if (glasses.state.state == AiLens.State.CONNECTED) {
                viewModelScope.launch {
//                    _navigationEvent.emit(glasses)
                }
            } else if (glasses.state.state == AiLens.State.DISCONNECTED) {
                BLEService.instance.retrieve()
            }
        } else if (glasses.state.state == AiLens.State.AVAILABLE) {
            glasses.connect()
        }
    }
}
