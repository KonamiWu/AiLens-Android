package com.konami.ailens.orchestrator.role

import android.util.Log
import com.konami.ailens.agent.AgentService
import com.konami.ailens.orchestrator.capability.AgentCapability
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.ToolCapability
import com.konami.ailens.recorder.Recorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AgentRole(override var toolCapability: ToolCapability, recorder: Recorder): Role, AgentCapability {
    private val service = AgentService.instance
    private val _isStart = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    override val isStart = _isStart.asSharedFlow()

    private val _isReady = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    override val isReady = _isReady.asSharedFlow()

    private val _answer = MutableStateFlow<String>("")
    override val answer = _answer.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        service.setRecorder(recorder)
        bind()
    }

    override fun registerCapabilities(sink: CapabilitySink) {
        sink.setAgent(this)
    }

    override fun startAgent() {
        service.start()
    }

    override fun stopAgent() {
        service.stop()
    }

    override fun replyNavigationError(message: String) {
        service.replyNavigationError(message)
    }

    private fun bind() {
        scope.launch {
            service.outputTranscript.collect {
                _answer.value = it
            }
        }
    }
}

