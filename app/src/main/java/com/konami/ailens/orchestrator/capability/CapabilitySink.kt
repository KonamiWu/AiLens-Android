package com.konami.ailens.orchestrator.capability

interface CapabilitySink {
    fun setAgent(role: AgentCapability)
    fun addAgentDisplay(role: AgentDisplayCapability)
}