package com.konami.ailens.function

import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.OpenCanvasCommand

class OpenCanvasListItem(val session: Glasses): ListItem {
    override val title: String = "Open Canvas"
    override fun execute() {
        OpenCanvasCommand().execute(session)
    }
}