package com.konami.ailens.translation.interpretation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.InterpretationDisplayCapability
import com.konami.ailens.orchestrator.role.Role
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class InterpretationViewModel: ViewModel(), Role, InterpretationDisplayCapability {
    private var _partialFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val partialFlow = _partialFlow.asSharedFlow()
    private var _resultFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resultFlow = _resultFlow.asSharedFlow()

    private var _isStop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val isStop = _isStop.asSharedFlow()

    var partial: Pair<String, String>? = null
    var history = mutableListOf<Pair<String, String>>()

    init {
        Orchestrator.instance.register(this)
    }

    override fun registerCapabilities(sink: CapabilitySink) {
        sink.addInterpretationDisplay(this)
    }

    override fun displayPartial(transcription: String, translation: String) {
        partial = Pair(transcription, translation)
        viewModelScope.launch {
            _partialFlow.emit(Unit)
        }
    }

    override fun displayResult(transcription: String, translation: String) {
        partial = null

        if (transcription.isNotEmpty() && translation.isNotEmpty()) {
            val value = Pair(transcription, translation)
            history.add(value)
        }
        viewModelScope.launch {
            _resultFlow.emit(Unit)
        }
    }

    override fun displayStart() {
    }

    override fun displayStop() {
        viewModelScope.launch {
            _isStop.emit(Unit)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Orchestrator.instance.unregister(this)
    }
    fun stop() {
        Orchestrator.instance.stopInterpretation()
    }
}