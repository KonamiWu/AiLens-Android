package com.konami.ailens.orchestrator.capability

interface DialogDisplayCapability {
    fun displayStartDialogTranslation()
    fun displayStopDialogTranslation()
    fun displaySourcePartial(transcription: String, translation: String)
    fun displaySourceResult(transcription: String, translation: String)

    fun displayTargetPartial(transcription: String, translation: String)
    fun displayTargetResult(transcription: String, translation: String)
    fun displayMicSwitch(side: DialogTranslationCapability.MicSide)
}