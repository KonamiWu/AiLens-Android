package com.konami.ailens.ble.command

import android.util.Log
import com.konami.ailens.ble.Glasses

class GetPowerSavingCommand : BLECommand<Boolean>() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x9B.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    override fun parseResult(data: ByteArray): Result<Boolean> {
        if (data.isEmpty())
            return Result.failure(Exception())
        val result = data.last() == 0x01.toByte()

        return Result.success(result)
    }
}