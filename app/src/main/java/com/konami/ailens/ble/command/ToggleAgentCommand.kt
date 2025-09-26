package com.konami.ailens.ble.command

import com.konami.ailens.ble.DeviceSession

class ToggleAgentCommand(private val isOn: Boolean) : VoidCommand() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0xCC.toByte(), 0x00, 0x01, 0x00, 0x02, 0x00, if (isOn) 0x01 else 0x00)

    override fun execute(session: DeviceSession) {
        session.sendRaw(getData())
    }
}