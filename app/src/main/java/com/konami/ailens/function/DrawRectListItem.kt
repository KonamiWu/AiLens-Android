package com.konami.ailens.function

import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.DrawRectCommand

class DrawRectListItem(private val session: Glasses,
                       private val x: Int,
                       private val y: Int,
                       private val width: Int,
                       private val height: Int,
                       private val lineWidth: Int,
                       private val fill: Boolean): ListItem {
    override val title: String = "Draw Rect"
    override fun execute() {
        DrawRectCommand(x, y, width, height, lineWidth, fill).execute(session)
    }
}