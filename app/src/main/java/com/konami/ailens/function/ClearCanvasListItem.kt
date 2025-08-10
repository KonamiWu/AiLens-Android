package com.konami.ailens.function

import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.command.ClearCanvasCommand

class ClearCanvasListItem(val session: DeviceSession): ListItem {
    override val title: String = "clear Canvas"
    override fun execute() {
        ClearCanvasCommand(session).executeBLE()
    }
}