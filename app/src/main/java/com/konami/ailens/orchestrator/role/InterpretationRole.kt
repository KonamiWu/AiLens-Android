package com.konami.ailens.orchestrator.role

import android.content.Context
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.InterpretationCapability
import com.konami.ailens.recorder.Recorder
import com.konami.ailens.translation.interpretation.InterpretationService
import kotlinx.coroutines.flow.SharedFlow

class InterpretationRole(private val context: Context, private val recorder: Recorder): Role, InterpretationCapability {
    private val service = InterpretationService(context, Orchestrator.instance.interpretationSourceLanguage, Orchestrator.instance.interpretationTargetLanguage, recorder)

    override val isStart: SharedFlow<Boolean>
        get() = service.isStart
    override val partialFlow: SharedFlow<Pair<String, String>>
        get() = service.partialFlow
    override val resultFlow: SharedFlow<Pair<String, String>>
        get() = service.resultFlow
    override val speechEnd: SharedFlow<Unit>
        get() = service.speechEnd

    override fun registerCapabilities(sink: CapabilitySink) {
        sink.setInterpretation(this)
    }

    override fun start() {
        service.start()
        service.startRecording()
    }

    override fun stop() {
        service.stop()
    }

    override fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language) {
        service.config(sourceLanguage, targetLanguage)
    }
}