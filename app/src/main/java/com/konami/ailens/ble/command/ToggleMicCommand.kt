package com.konami.ailens.ble.command

import com.konami.ailens.ble.DeviceSession

/**
 * Command to open the microphone on glasses.
 */
class ToggleMicCommand(private val isOn: Boolean) : VoidCommand() {

    override fun execute(session: DeviceSession) {
        // Send raw command bytes to device
        val value = byteArrayOf(
            0x45, 0x4D,
            0x2F, 0x00,
            0x01, 0x00,
            0x02, 0x00,
            if (isOn) 0x01 else 0x00
        )
        session.sendRaw(value)
    }
}
