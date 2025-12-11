package com.konami.ailens.ble.command.ota

import android.util.Log
import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.VoidCommand

/**
 * Set device to OTA mode
 * Command: 0x7D (CMD_OTA_SET_OTA_MODE_7D)
 * Reference iOS: CMD_OTA_SET_OTA_MODE_7D
 */
class SetOTAModeCommand : VoidCommand() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x7D, 0x00, 0x00, 0x00, 0x00, 0x00)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    companion object {
        private const val TAG = "SetOTAModeCommand"
    }
}
