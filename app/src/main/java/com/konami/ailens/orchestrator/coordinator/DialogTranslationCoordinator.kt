package com.konami.ailens.orchestrator.coordinator

import android.app.Dialog
import android.util.Log
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.DialogDisplayCapability
import com.konami.ailens.orchestrator.capability.DialogTranslationCapability
import com.konami.ailens.translation.dialog.DialogTranslationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DialogTranslationCoordinator(private val dialog: DialogTranslationCapability, private var dialogDisplays: List<DialogDisplayCapability>) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var isStarted: Boolean = false

    init {
        scope.launch {
            dialog.isStart.collect { isStart ->
                if (isStart) {
                    dialogDisplays.forEach {
                        it.displayStartDialogTranslation()
                    }
                } else {
                    dialogDisplays.forEach {
                        it.displayStopDialogTranslation()
                    }
                }
            }
        }

        scope.launch {
            dialog.sourcePartialFlow.collect { partial ->
                dialogDisplays.forEach {
                    it.displaySourcePartial(partial.first, partial.second)
                }
            }
        }

        scope.launch {
            dialog.sourceResultFlow.collect { partial ->
                dialogDisplays.forEach {
                    it.displaySourceResult(partial.first, partial.second)
                }
            }
        }

        scope.launch {
            dialog.targetPartialFlow.collect { partial ->
                dialogDisplays.forEach {
                    it.displayTargetPartial(partial.first, partial.second)
                }
            }
        }

        scope.launch {
            dialog.targetResultFlow.collect { partial ->
                dialogDisplays.forEach {
                    it.displayTargetResult(partial.first, partial.second)
                }
            }
        }
    }

    fun updateDisplay(displayCapability: DialogDisplayCapability) {
        val new = dialogDisplays.toMutableList()
        new.add(displayCapability)
        dialogDisplays = new
    }

    fun start(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language, side: DialogTranslationCapability.MicSide) {
        isStarted = true
        dialog.start(sourceLanguage, targetLanguage, side)
        dialogDisplays.forEach {
            it.displayMicSwitch(side)
        }
    }

    fun stop() {
        isStarted = false
        dialog.stop()
        scope.cancel()
        dialogDisplays.forEach {
            it.displayStopDialogTranslation()
        }
    }

    fun switchRecorder(side: DialogTranslationCapability.MicSide) {
        dialog.switchRecorder(side)
        dialogDisplays.forEach {
            it.displayMicSwitch(side)
        }
    }

    fun stopRecording(side: DialogTranslationCapability.MicSide) {
        dialog.stopRecording(side)
    }
}