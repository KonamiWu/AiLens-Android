package com.konami.ailens.translation.dialog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.DialogDisplayCapability
import com.konami.ailens.orchestrator.capability.DialogTranslationCapability
import com.konami.ailens.orchestrator.role.Role
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DialogTranslationViewModel: ViewModel(), Role, DialogDisplayCapability {
    interface DialogTranslationMessage {
        val viewType: Int
        fun onBindViewHolder(holder: RecyclerView.ViewHolder)
    }

    class DialogTranslationLeftMessage(private val transcription: String, private val translation: String): DialogTranslationMessage {
        override val viewType: Int = DialogTranslationFragment.Adapter.LEFT_MESSAGE
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder) {
            (holder as DialogTranslationFragment.Adapter.MessageViewHolder).textView.text = translation
        }
    }

    class DialogTranslationRightMessage(private val transcription: String, private val translation: String): DialogTranslationMessage {
        override val viewType: Int = DialogTranslationFragment.Adapter.RIGHT_MESSAGE
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder) {
            (holder as DialogTranslationFragment.Adapter.MessageViewHolder).textView.text = transcription
        }
    }

    private var _leftPartialFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val leftPartialFlow = _leftPartialFlow.asSharedFlow()
    private val _leftResultFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val leftResultFlow = _leftResultFlow.asSharedFlow()
    private val _rightPartialFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val rightPartialFlow = _rightPartialFlow.asSharedFlow()
    private val _rightResultFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val rightResultFlow = _rightResultFlow.asSharedFlow()

    val sourceLanguageTitle: String get() = Orchestrator.Companion.instance.dialogSourceLanguage.title
    val targetLanguageTitle: String get() = Orchestrator.Companion.instance.dialogTargetLanguage.title

    private val _micSide = MutableSharedFlow<DialogTranslationCapability.MicSide>(extraBufferCapacity = 1)
    val micSide = _micSide.asSharedFlow()
    private val _stop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stop = _stop.asSharedFlow()

    private val _messages = mutableListOf<DialogTranslationMessage>()
    val messages: List<DialogTranslationMessage> get() = _messages

    init {
        Orchestrator.instance.register(this)
    }

    override fun registerCapabilities(sink: CapabilitySink) {
        sink.addDialogDisplay(this)
    }

    override fun displayStartDialogTranslation() {

    }

    override fun displayStopDialogTranslation() {
        _stop.tryEmit(Unit)
    }

    override fun displaySourcePartial(transcription: String, translation: String) {
        if (translation.isEmpty() || translation.isEmpty())
            return

        _leftPartialFlow.tryEmit(Pair(transcription, translation))
    }

    override fun displaySourceResult(transcription: String, translation: String) {
        if (translation.isEmpty() || translation.isEmpty())
            return

        _messages.add(DialogTranslationLeftMessage(transcription, translation))
        _leftResultFlow.tryEmit(Pair(transcription, translation))
    }

    override fun displayTargetPartial(transcription: String, translation: String) {
        if (translation.isEmpty() || translation.isEmpty())
            return

        _rightPartialFlow.tryEmit(Pair(transcription, translation))
    }

    override fun displayTargetResult(transcription: String, translation: String) {
        if (translation.isEmpty() || translation.isEmpty())
            return

        _messages.add(DialogTranslationRightMessage(transcription, translation))
        _rightResultFlow.tryEmit(Pair(transcription, translation))
    }

    override fun displayMicSwitch(side: DialogTranslationCapability.MicSide) {
        viewModelScope.launch {
            _micSide.emit(side)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Orchestrator.Companion.instance.unregister(this)
    }
}