package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

class SetBrightnessCommand(private val brightness: Int) : VoidCommand() {
    private fun getData() = byteArrayOf(
        0x45, 0x4D,
        0x21, 0x00,
        0x01, 0x00,
        0x02, 0x00,
        (brightness and 0xFF).toByte()
    )

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}
