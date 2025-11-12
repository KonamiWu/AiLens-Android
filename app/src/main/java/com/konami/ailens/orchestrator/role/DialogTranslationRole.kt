package com.konami.ailens.orchestrator.role

import android.content.Context
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.DialogTranslationCapability
import com.konami.ailens.orchestrator.capability.InterpretationCapability
import com.konami.ailens.recorder.Recorder
import com.konami.ailens.translation.dialog.DialogTranslationService
import kotlinx.coroutines.flow.SharedFlow


class DialogTranslationRole(private val context: Context, val sourceRecorder: Recorder, val targetRecorder: Recorder): Role, DialogTranslationCapability {
    private val service = DialogTranslationService(context, Orchestrator.instance.interpretationSourceLanguage, Orchestrator.instance.interpretationTargetLanguage, sourceRecorder, targetRecorder)

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

    override fun start() {
        service.start()
    }

    override fun stop() {
        service.stop()
    }

    override fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language) {
        service.config(sourceLanguage, targetLanguage)
    }
}