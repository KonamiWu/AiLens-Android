package com.konami.ailens.ble.command.ota

import android.util.Log
import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.VoidCommand

/**
 * Reboot device
 * Command: 0x1B (CMD_REBOOT_1B)
 * Reference iOS: CMD_REBOOT_1B
 */
class RebootCommand : VoidCommand() {
    private fun getData(): ByteArray {
        return byteArrayOf(
            0x45, 0x4D,
            0x1B, 0x00,
            0x01, 0x00,
            0x02, 0x00,
            0x10
        )
    }

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}
