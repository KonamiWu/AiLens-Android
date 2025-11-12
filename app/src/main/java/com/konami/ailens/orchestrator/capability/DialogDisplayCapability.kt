package com.konami.ailens.orchestrator.capability

interface DialogDisplayCapability {
    fun displayStart()
    fun displayStop()
    fun displaySourcePartial(transcription: String, translation: String)
    fun displaySourceResult(transcription: String, translation: String)

    fun displayTargetPartial(transcription: String, translation: String)
    fun displayTargetResult(transcription: String, translation: String)
}