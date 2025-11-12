package com.konami.ailens.translation

import android.content.Context
import com.konami.ailens.R
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.recorder.Recorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class InterpretationService(private val context: Context, private val sourceLanguage: Orchestrator.Language,
    private var targetLanguage: Orchestrator.Language, private val recorder: Recorder) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var translator = AzureTranslator(scope, sourceLanguage.code, targetLanguage.code, context.getString(R.string.azure_token))
    private val _isStart = MutableSharedFlow<Boolean>()
    val isStart = _isStart.asSharedFlow()
    private val _isReady = MutableSharedFlow<Boolean>()
    val isReady = _isReady.asSharedFlow()

    private val _partialFlow = MutableSharedFlow<Pair<String, String>>()
    val partialFlow = _partialFlow.asSharedFlow()

    private val _resultFlow = MutableSharedFlow<Pair<String, String>>()
    val resultFlow = _resultFlow.asSharedFlow()

    private val _speechEnd = MutableSharedFlow<Unit>()
    val speechEnd = _speechEnd.asSharedFlow()

    init {
        scope.launch {
            translator.partialFlow.collect {
                _partialFlow.tryEmit(it)
            }
        }

        scope.launch {
            translator.resultFlow.collect {
                _resultFlow.tryEmit(it)
            }
        }

        scope.launch {
            translator.isReady.collect {
                _isReady.tryEmit(it)
            }
        }

        scope.launch {
            translator.speechEnd.collect {
                _speechEnd.tryEmit(it)
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
            _isStart.tryEmit(true)
        }
    }

    fun stop() {
        recorder.stopRecording()
        recorder.close()
        scope.launch {
            _isStart.tryEmit(false)
        }
    }

    fun startRecording() {
        recorder.startRecording()
    }

    fun stopRecording() {
        recorder.stopRecording()
    }

    fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language) {
        translator = AzureTranslator(scope, sourceLanguage.code, targetLanguage.code, context.getString(R.string.azure_token))
    }
}