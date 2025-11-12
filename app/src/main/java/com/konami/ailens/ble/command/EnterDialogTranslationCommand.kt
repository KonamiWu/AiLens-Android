package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses


class EnterDialogTranslationCommand : VoidCommand() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x30, 0x00, 0x02, 0x00, 0x04, 0x00, 0x00/*role*/, 0x00/*language*/)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}