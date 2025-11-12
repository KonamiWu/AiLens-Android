package com.konami.ailens.orchestrator.capability

interface InterpretationDisplayCapability {
    fun displayPartial(transcription: String, translation: String)
    fun displayResult(transcription: String, translation: String)
    fun displayStart()
    fun displayStop()
}