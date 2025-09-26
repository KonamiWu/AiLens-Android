package com.konami.ailens.function

import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.command.CloseCanvasCommand


class CloseCanvasListItem(val session: DeviceSession): ListItem {
    override val title: String = "close Canvas"
    override fun execute() {
        CloseCanvasCommand().execute(session)
    }
}