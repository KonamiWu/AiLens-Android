package com.konami.ailens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.Glasses
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AddDeviceSearchViewModel: ViewModel() {
    private val _updateFlow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val updateFlow = _updateFlow.asSharedFlow()

    private var searchJob: Job? = null

    fun startSearch() {
        // Cancel previous search if exists
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            BLEService.instance.updateFlow.collect {
                _updateFlow.tryEmit(Unit)
            }
        }

        BLEService.instance.startScan()
    }

    fun stopSearch() {
        BLEService.instance.stopScan()
    }

    fun getItems(): List<Glasses> {
        return BLEService.instance.sessions.values.toList()
    }
}