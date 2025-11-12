package com.konami.ailens.orchestrator.coordinator

import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.InterpretationCapability
import com.konami.ailens.orchestrator.capability.InterpretationDisplayCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InterpretationCoordinator(private val interpretation: InterpretationCapability, private val interpretationDisplays: List<InterpretationDisplayCapability>) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            interpretation.isStart.collect { isStart ->
                if (isStart) {
                    interpretationDisplays.forEach {
                        it.displayStart()
                    }
                } else {
                    interpretationDisplays.forEach {
                        it.displayStop()
                    }
                }
            }
        }

        scope.launch {
            interpretation.isReady.collect {
                interpretationDisplays.forEach {
                    it.displayReady()
                }
            }
        }

        scope.launch {
            interpretation.partialFlow.collect { partial ->
                interpretationDisplays.forEach {
                    it.displayPartial(partial.first, partial.second)
                }
            }
        }

        scope.launch {
            interpretation.resultFlow.collect { result ->
                interpretationDisplays.forEach {
                    it.displayResult(result.first, result.second)
                }
            }
        }
    }

    fun start() {
        interpretation.start()
    }

    fun stop() {
        interpretation.stop()
    }

    fun startRecording() {
        interpretation.startRecording()
    }

    fun stopRecording() {
        interpretation.stopRecording()
    }

    fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language) {
        interpretation.config(sourceLanguage, targetLanguage)
    }
}