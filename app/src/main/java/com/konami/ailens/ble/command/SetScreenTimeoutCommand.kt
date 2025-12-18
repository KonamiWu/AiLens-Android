package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

class SetScreenTimeoutCommand(private val seconds: Int) : VoidCommand() {
    private fun getData(): ByteArray {
        return byteArrayOf(
            0x45, 0x4D,
            0x3C, 0x00,
            0x04, 0x00,
            0x08, 0x00,
            (seconds and 0xFF).toByte(),
            ((seconds shr 8) and 0xFF).toByte(),
            ((seconds shr 16) and 0xFF).toByte(),
            ((seconds shr 24) and 0xFF).toByte()
        )
    }

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}
