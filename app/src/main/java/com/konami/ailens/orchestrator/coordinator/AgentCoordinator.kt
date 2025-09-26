package com.konami.ailens.orchestrator.coordinator

import android.util.Log
import com.konami.ailens.orchestrator.capability.AgentCapability
import com.konami.ailens.orchestrator.capability.AgentDisplayCapability
import com.konami.ailens.orchestrator.capability.ToolCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AgentCoordinator(private val agentCapability: AgentCapability,
                       private val  agentDisplayCapabilities: List<AgentDisplayCapability>,
                       private val toolCapability: ToolCapability) {

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    init {
        scope.launch {
            agentCapability.isStart.collect { isStart ->
                agentDisplayCapabilities.forEach {
                    it.displayStartAgent()
                }
            }
        }

        scope.launch {
            agentCapability.isReady.collect { isStart ->
                agentDisplayCapabilities.forEach {
                    it.displayStartAgent()
                }
            }
        }

        scope.launch {
            agentCapability.answer.collect { answer ->
                Log.e("TAG", "answer = ${answer}")
                agentDisplayCapabilities.forEach { display ->
                    display.displayResultAnswer(answer)
                }
            }
        }

        agentCapability.toolCapability = toolCapability
    }

    fun start() {
        agentCapability.startAgent()
        agentDisplayCapabilities.forEach {
            it.displayStartAgent()
        }
    }

    fun stop() {
        agentCapability.stopAgent()
        agentDisplayCapabilities.forEach {
            it.displayStopAgent()
        }
    }
}