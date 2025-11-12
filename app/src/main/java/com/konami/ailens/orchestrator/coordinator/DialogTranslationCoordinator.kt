package com.konami.ailens.orchestrator.coordinator

import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.DialogDisplayCapability
import com.konami.ailens.orchestrator.capability.DialogTranslationCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DialogTranslationCoordinator(private val dialog: DialogTranslationCapability, private val dialogDisplays: List<DialogDisplayCapability>) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            dialog.isStart.collect { isStart ->
                if (isStart) {
                    dialogDisplays.forEach {
                        it.displayStart()
                    }
                } else {
                    dialogDisplays.forEach {
                        it.displayStop()
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

    fun start() {
        dialog.start()
    }

    fun stop() {
        dialog.stop()
        scope.cancel()
        dialogDisplays.forEach {
            it.displayStop()
        }
    }

    fun config(sourceLanguage: Orchestrator.Language, targetLanguage: Orchestrator.Language) {
        dialog.config(sourceLanguage, targetLanguage)
    }
}