package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

class SetPowerSavingCommand(private val onOff: Boolean) : VoidCommand() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x9A.toByte(), 0x00, 0x01, 0x00, 0x02, 0x00, if(onOff) 0x01 else 0x00)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}