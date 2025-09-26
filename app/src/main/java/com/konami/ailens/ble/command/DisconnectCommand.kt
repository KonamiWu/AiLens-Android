package com.konami.ailens.ble.command

import com.konami.ailens.ble.DeviceSession

class DisconnectCommand() : VoidCommand() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x73, 0x00, 0x00, 0x00, 0x00, 0x00)

    override fun execute(session: DeviceSession) {
        session.sendRaw(getData())
    }
}