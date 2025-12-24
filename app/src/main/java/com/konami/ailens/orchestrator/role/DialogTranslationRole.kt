package com.konami.ailens.orchestrator.role

import android.content.Context
import android.util.Log
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.DialogTranslationCapability
import com.konami.ailens.orchestrator.capability.InterpretationCapability
import com.konami.ailens.recorder.Recorder
import com.konami.ailens.translation.dialog.DialogTranslationService
import kotlinx.coroutines.flow.SharedFlow


class DialogTranslationRole(private val context: Context, val sourceRecorder: Recorder, val targetRecorder: Recorder): Role, DialogTranslationCapability {
    private val service = DialogTranslationService(context, sourceRecorder, targetRecorder)

    override val isStart: SharedFlow<Boolean>
        get() = service.isStart
    override val sourcePartialFlow: SharedFlow<Pair<String, String>>
        get() = service.sourcePartialFlow
    override val sourceResultFlow: SharedFlow<Pair<String, String>>
        get() = service.sourceResultFlow
    override val sourceSpeechEnd: SharedFlow<Unit>
        get() = service.sourceSpeechEnd

    override val targetPartialFlow: SharedFlow<Pair<String, String>>
        get() = service.targetPartialFlow
    override val targetResultFlow: SharedFlow<Pair<String, String>>
        get() = service.targetResultFlow
    override val targetSpeechEnd: SharedFlow<Unit>
        get() = service.targetSpeechEnd

    override fun registerCapabilities(sink: CapabilitySink) {
        sink.setDialogTranslation(this)
    }

    override fun start(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language, side: DialogTranslationCapability.MicSide) {
        when (side) {
            DialogTranslationCapability.MicSide.SOURCE -> {
                service.start(sourceLanguage, targetLanguage, DialogTranslationService.MicSide.SOURCE)
            }
            DialogTranslationCapability.MicSide.TARGET -> {
                service.start(sourceLanguage, targetLanguage, DialogTranslationService.MicSide.TARGET)
            }
        }
    }

    override fun switchRecorder(side: DialogTranslationCapability.MicSide) {
        when (side) {
            DialogTranslationCapability.MicSide.SOURCE -> {
                service.switchRecorder(DialogTranslationService.MicSide.SOURCE)
            }
            DialogTranslationCapability.MicSide.TARGET -> {
                service.switchRecorder(DialogTranslationService.MicSide.TARGET)
            }
        }
    }

    override fun stopRecording(side: DialogTranslationCapability.MicSide) {
        when (side) {
            DialogTranslationCapability.MicSide.SOURCE -> {
                service.stopRecording(DialogTranslationService.MicSide.SOURCE)
            }
            DialogTranslationCapability.MicSide.TARGET -> {
                service.stopRecording(DialogTranslationService.MicSide.TARGET)
            }
        }
    }

    override fun stop() {
        service.stop()
    }
}