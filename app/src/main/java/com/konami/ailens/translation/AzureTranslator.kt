package com.konami.ailens.translation

import android.util.Log
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream
import com.microsoft.cognitiveservices.speech.translation.SpeechTranslationConfig
import com.microsoft.cognitiveservices.speech.translation.TranslationRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AzureTranslator(
    val scope: CoroutineScope,

    val token: String
) {
    val TAG = "MainViewModel"
    private val region = "southeastasia"
    private var translator: TranslationRecognizer? = null
    private val pushStream: PushAudioInputStream
    private val _isReady = MutableSharedFlow<Boolean>()
    val isReady = _isReady.asSharedFlow()
    private val _partialFlow = MutableSharedFlow<Pair<String, String>>()
    val partialFlow = _partialFlow.asSharedFlow()

    private val _resultFlow = MutableSharedFlow<Pair<String, String>>()
    val resultFlow = _resultFlow.asSharedFlow()

    private val _speechEnd = MutableSharedFlow<Unit>()
    val speechEnd = _speechEnd.asSharedFlow()



    init {

        val audioFormat = AudioStreamFormat.getWaveFormatPCM(16000, 16, 1)
        pushStream = AudioInputStream.createPushStream(audioFormat)

    }

    fun start(source: String, target: String) {
        translator?.startContinuousRecognitionAsync()
        val config = SpeechTranslationConfig.fromSubscription(token.trim(), region)
        config.setProperty("SpeechServiceConnection_EndSilenceTimeoutMs", "1000")

        config.speechRecognitionLanguage = source
        config.addTargetLanguage(target)
        val translator = TranslationRecognizer(config, AudioConfig.fromStreamInput(pushStream))


        translator.sessionStarted.addEventListener { _, e ->
            Log.e(TAG, "🟡 Session start: ${e.sessionId}")
            _isReady.tryEmit(true)
        }
        translator.sessionStopped.addEventListener { _, e ->
            Log.e(TAG, "🟡 Session stopped: ${e.sessionId}")
        }
        translator.speechStartDetected.addEventListener { _, _ ->
            Log.e(TAG, "🔊 Speech start detected")
        }
        translator.speechEndDetected.addEventListener { _, _ ->
            Log.e(TAG, "🔇 Speech end detected")
            _speechEnd.tryEmit(Unit)
        }
        translator.recognizing.addEventListener { _, event ->
            Log.e(TAG, "✏️ Partial: ${event.result.text}")
            val translation = event.result.translations.values.firstOrNull() ?: ""
            scope.launch {
                _partialFlow.emit(Pair(event.result.text, translation))
            }
        }

        translator.recognized.addEventListener { _, event ->
            val translation = event.result.translations.values.firstOrNull() ?: ""
            Log.e("TAG", "event.result = ${event.result.text}, translation = ${event.result.translations.values.first()}")

            scope.launch {
                _resultFlow.emit(Pair(event.result.text, translation))
            }
        }

        this.translator = translator
    }

    fun stop() {
        translator?.stopContinuousRecognitionAsync()
    }

    fun push(pcm: ByteArray) {
        pushStream.write(pcm)
    }
}