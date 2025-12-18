package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

class GetAutomaticBrightnessCommand : BLECommand<Boolean>() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    override fun parseResult(data: ByteArray): Result<Boolean> {
        if (data.size < 9 || data[8] != 0x00.toByte())
            return Result.failure(Exception())

        val isAutomatic = data[9] == 0x00.toByte()
        return Result.success(isAutomatic)
    }
}
