package com.konami.ailens.translation.dialog

import android.content.Context
import com.konami.ailens.R
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.recorder.Recorder
import com.konami.ailens.translation.AzureTranslator
import com.konami.ailens.translation.interpretation.InterpretationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DialogTranslationService(private val context: Context, private val sourceLanguage: Orchestrator.Language,
    targetLanguage: Orchestrator.Language, private val sourceRecorder: Recorder, private val targetRecorder: Recorder) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var sourceTranslationService = InterpretationService(context, sourceLanguage, targetLanguage, sourceRecorder)
    private var targetTranslationService = InterpretationService(context, targetLanguage, sourceLanguage, targetRecorder)
    
    private val _isStart = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val isStart = _isStart.asSharedFlow()
    
    val sourcePartialFlow = sourceTranslationService.partialFlow

    val sourceResultFlow = sourceTranslationService.resultFlow
    val sourceSpeechEnd = sourceTranslationService.speechEnd
    val targetPartialFlow = targetTranslationService.partialFlow
    val targetResultFlow = targetTranslationService.resultFlow
    val targetSpeechEnd = targetTranslationService.speechEnd

    init {

    }

    fun start() {
        sourceTranslationService.start()
        targetTranslationService.start()
        scope.launch {
            _isStart.emit(true)
        }
    }

    fun stop() {
        sourceTranslationService.stop()
        targetTranslationService.stop()
        scope.launch {
            _isStart.emit(false)
        }
    }

    fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language) {
        sourceTranslationService.config(sourceLanguage, targetLanguage)
        targetTranslationService.config(targetLanguage, sourceLanguage)
    }
}