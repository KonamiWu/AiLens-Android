package com.konami.ailens.translation.interpretation

import android.content.Context
import com.konami.ailens.R
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.recorder.Recorder
import com.konami.ailens.translation.AzureTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class InterpretationService(private val context: Context, private val sourceLanguage: Orchestrator.Language,
    targetLanguage: Orchestrator.Language, private val recorder: Recorder) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var translator = AzureTranslator(scope, sourceLanguage.code, targetLanguage.code, context.getString(R.string.azure_token))
    private val _isStart = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val isStart = _isStart.asSharedFlow()
    private val _partialFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val partialFlow = _partialFlow.asSharedFlow()

    private val _resultFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val resultFlow = _resultFlow.asSharedFlow()

    private val _speechEnd = MutableSharedFlow<Unit>()
    val speechEnd = _speechEnd.asSharedFlow()

    init {
        scope.launch {
            translator.partialFlow.collect {
                _partialFlow.emit(it)
            }
        }

        scope.launch {
            translator.resultFlow.collect {
                _resultFlow.emit(it)
            }
        }

        scope.launch {
            translator.speechEnd.collect {
                _speechEnd.emit(it)
            }
        }

        scope.launch {
            recorder.frames.collect {
                translator.push(it)
            }
        }
    }

    fun start() {
        translator.start()
        scope.launch {
            _isStart.emit(true)
        }
    }

    fun stop() {
        translator.stop()
        recorder.stopRecording()
        scope.launch {
            _isStart.emit(false)
        }
    }

    fun startRecording() {
        recorder.startRecording()
    }

    fun stopRecording() {
        recorder.stopRecording()
    }

    fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language) {
        translator.stop()
        translator = AzureTranslator(scope, sourceLanguage.code, targetLanguage.code, context.getString(R.string.azure_token))
    }
}