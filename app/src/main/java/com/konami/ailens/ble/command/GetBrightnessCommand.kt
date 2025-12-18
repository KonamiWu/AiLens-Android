package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

class GetBrightnessCommand : BLECommand<Int>() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x22, 0x00, 0x00, 0x00, 0x00, 0x00)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    override fun parseResult(data: ByteArray): Result<Int> {
        if (data.size < 10 || data[8] != 0x00.toByte())
            return Result.failure(Exception())

        val brightness = data[9].toInt() and 0xFF
        return Result.success(brightness)
    }
}
