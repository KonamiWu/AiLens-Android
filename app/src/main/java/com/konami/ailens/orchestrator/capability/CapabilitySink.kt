package com.konami.ailens.orchestrator.capability

interface CapabilitySink {
    fun setAgent(role: AgentCapability)
    fun addAgentDisplay(role: AgentDisplayCapability)
    fun addNavigationDisplay(role: NavigationDisplayCapability)
    fun addNavigation(role: NavigationCapability)

    fun setInterpretation(role: InterpretationCapability)
    fun addInterpretationDisplay(role: InterpretationDisplayCapability)

    fun setDialogTranslation(role: DialogTranslationCapability)
    fun addDialogDisplay(role: DialogDisplayCapability)
}