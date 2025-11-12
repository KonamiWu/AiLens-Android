package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

class OpenCanvasCommand : VoidCommand() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x79, 0x00, 0x00, 0x00, 0x00, 0x00)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}