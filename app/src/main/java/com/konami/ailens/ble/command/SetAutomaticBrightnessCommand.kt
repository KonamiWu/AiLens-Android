package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

class SetAutomaticBrightnessCommand(private val isAutomatic: Boolean) : VoidCommand() {
    private fun getData() = byteArrayOf(
        0x45, 0x4D,
        0x1F, 0x00,
        0x01, 0x00,
        0x02, 0x00,
        if (isAutomatic) 0x00 else 0x01
    )

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}
