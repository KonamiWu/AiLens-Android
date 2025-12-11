package com.konami.ailens.ble.command.ota

import android.util.Log
import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.VoidCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Update device version information
 * Command: 0x2B (CMD_SET_UPD_2B)
 * Reference iOS: refreshVersion method - sends BAG header version in big-endian
 */
class SetVersionCommand(private val version: UInt) : VoidCommand() {
    private fun getData(): ByteArray {
        // Build complete frame with 4-byte big-endian version
        val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0x4D45.toShort())  // magic
        buffer.putShort(0x002B.toShort())  // command
        buffer.putShort(0x0004.toShort())  // total_len (4 bytes of data)

        // Bitfield: bit 0 = packet_type (0), bits 1-15 = value_len (4)
        // value = packet_type | (value_len << 1) = 0 | (4 << 1) = 8
        buffer.putShort(0x0008.toShort())  // packet_type (1bit) + value_len (15bits)

        // Version in big-endian format (per iOS refreshVersion)
        val versionInt = version.toInt()
        buffer.put(((versionInt shr 24) and 0xFF).toByte())
        buffer.put(((versionInt shr 16) and 0xFF).toByte())
        buffer.put(((versionInt shr 8) and 0xFF).toByte())
        buffer.put((versionInt and 0xFF).toByte())

        return buffer.array()
    }

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
        Log.e(TAG, "Set Version: version=$version (0x${version.toString(16)})")
    }

    companion object {
        private const val TAG = "SetVersionCommand"
    }
}
