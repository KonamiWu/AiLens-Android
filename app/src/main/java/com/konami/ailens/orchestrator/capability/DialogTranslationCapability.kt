package com.konami.ailens.orchestrator.capability

import com.konami.ailens.orchestrator.Orchestrator
import kotlinx.coroutines.flow.SharedFlow

interface DialogTranslationCapability {
    enum class MicSide {
        SOURCE,
        TARGET
    }
    fun start(side: MicSide)
    fun switchRecorder(side: MicSide)
    fun stopRecording(side: MicSide)
    fun stop()
    fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language)
    val isStart: SharedFlow<Boolean>
    val sourcePartialFlow: SharedFlow<Pair<String, String>>
    val sourceResultFlow: SharedFlow<Pair<String, String>>
    val sourceSpeechEnd: SharedFlow<Unit>
    val targetPartialFlow: SharedFlow<Pair<String, String>>
    val targetResultFlow: SharedFlow<Pair<String, String>>
    val targetSpeechEnd: SharedFlow<Unit>
}