package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

class UnbindCommand() : VoidCommand() {

    override fun execute(session: Glasses) {
        val value = byteArrayOf(
            0x45, 0x4D,
            0x01, 0x00,
            0x00, 0x00,
            0x00, 0x00
        )
        session.sendRaw(value)
    }
}
