package com.konami.ailens.orchestrator.capability

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AgentCapability {
    val isStart: SharedFlow<Boolean>
    val isReady: SharedFlow<Boolean>
    val answer: StateFlow<String>
    var toolCapability: ToolCapability
    fun startAgent()
    fun stopAgent()
    fun replyNavigationError(message: String)
}