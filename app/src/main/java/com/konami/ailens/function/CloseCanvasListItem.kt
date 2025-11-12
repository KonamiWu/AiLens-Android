package com.konami.ailens.function

import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.CloseCanvasCommand


class CloseCanvasListItem(val session: Glasses): ListItem {
    override val title: String = "close Canvas"
    override fun execute() {
        CloseCanvasCommand().execute(session)
    }
}