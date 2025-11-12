package com.konami.ailens.orchestrator.capability

import com.konami.ailens.orchestrator.Orchestrator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface InterpretationCapability {
    fun start()
    fun stop()
    fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language)
    val isStart: SharedFlow<Boolean>
    val partialFlow: SharedFlow<Pair<String, String>>
    val resultFlow: SharedFlow<Pair<String, String>>
    val speechEnd: SharedFlow<Unit>
}