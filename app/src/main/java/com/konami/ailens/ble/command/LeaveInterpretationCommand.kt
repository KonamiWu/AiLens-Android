package com.konami.ailens.ble.command

import android.util.Log
import com.konami.ailens.ble.Glasses

class LeaveInterpretationCommand : VoidCommand() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0xC7.toByte(), 0x00, 0x01, 0x00, 0x02, 0x00, 0x00)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}