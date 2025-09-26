package com.konami.ailens.orchestrator.capability

interface AgentDisplayCapability {
    fun displayResultAnswer(result: String)
    fun displayStartAgent()
    fun displayStopAgent()
}