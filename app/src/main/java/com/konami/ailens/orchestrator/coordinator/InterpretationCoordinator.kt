package com.konami.ailens.orchestrator.coordinator

import android.util.Log
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.InterpretationCapability
import com.konami.ailens.orchestrator.capability.InterpretationDisplayCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
        scope.cancel()
        interpretationDisplays.forEach {
            it.displayStop()
        }
    }

    fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language) {
        interpretation.config(sourceLanguage, targetLanguage)
    }
}