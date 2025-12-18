package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

class GetScreenTimeoutCommand : BLECommand<Int>() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x3B.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    override fun parseResult(data: ByteArray): Result<Int> {
        if (data.size < 13 || data[8] != 0x00.toByte())
            return Result.failure(Exception())

        val timeout = ((data[9].toInt() and 0xFF)) or
                ((data[10].toInt() and 0xFF) shl 8) or
                ((data[11].toInt() and 0xFF) shl 16) or
                ((data[12].toInt() and 0xFF) shl 24)

        return Result.success(timeout)
    }
}