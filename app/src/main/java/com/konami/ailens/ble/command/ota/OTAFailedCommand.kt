package com.konami.ailens.ble.command.ota

import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.VoidCommand

class OTAFailedCommand : VoidCommand() {
    private fun getData(): ByteArray {
        return byteArrayOf(
            0x45, 0x4D,
            0x7C, 0x00,
            0x00, 0x00,
            0x00, 0x00,
        )
    }

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}