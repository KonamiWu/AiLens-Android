package com.konami.ailens.function

import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.ClearCanvasCommand

class ClearCanvasListItem(val session: Glasses): ListItem {
    override val title: String = "clear Canvas"
    override fun execute() {
        ClearCanvasCommand().execute(session)
    }
}