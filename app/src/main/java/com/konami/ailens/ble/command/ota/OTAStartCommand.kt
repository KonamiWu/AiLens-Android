package com.konami.ailens.ble.command.ota

import android.util.Log
import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.VoidCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Start OTA update with total data length
 * Command: 0x7B (CMD_OTA_UPDATE_START_7B)
 * Reference iOS: CMD_OTA_UPDATE_START_7B
 */
class OTAStartCommand(private val totalLength: UInt) : VoidCommand() {
    private fun getData(): ByteArray {
        // Build complete frame with 4-byte totalLength
        val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0x4D45.toShort())  // magic
        buffer.putShort(0x007B.toShort())  // command
        buffer.putShort(0x0004.toShort())  // total_len (4 bytes of data)

        // Bitfield: bit 0 = packet_type (0), bits 1-15 = value_len (4)
        // value = packet_type | (value_len << 1) = 0 | (4 << 1) = 8
        buffer.putShort(0x0008.toShort())  // packet_type (1bit) + value_len (15bits)

        buffer.putInt(totalLength.toInt())  // data: totalLength as UInt32
        return buffer.array()
    }

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
        Log.e(TAG, "OTA Start: totalLength=$totalLength")
    }

    companion object {
        private const val TAG = "OTAStartCommand"
    }
}
