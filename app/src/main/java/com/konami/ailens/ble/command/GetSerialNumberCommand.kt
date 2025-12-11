package com.konami.ailens.ble.command

import android.util.Log
import com.konami.ailens.ble.Glasses

/**
 * Get serial number command
 * Reference iOS: CMD_GET_SN = 0x4a
 */
class GetSerialNumberCommand : BLECommand<String>() {

    /**
     * Command format: 0x45 0x4D [cmd] 0x00 0x00 0x00 0x00 0x00
     * cmd = 0x4a for GET_SN
     */
    private fun getData() = byteArrayOf(
        0x45.toByte(), 0x4D.toByte(), // Header
        0x4a.toByte(),                // CMD_GET_SN
        0x00.toByte(), 0x00.toByte(), // Data length (0)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte()  // Padding
    )

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    override fun parseResult(data: ByteArray): Result<String> {
        return try {
            // Response format: 0x4F 0x42 [cmd] [len_low] [len_high] [data...]
            // data bytes contain serial number string
            if (data.size > 5) {
                val payload = data.copyOfRange(5, data.size)

                // Log raw bytes for debugging
                Log.e("GetSerialNumberCommand", "Raw payload: ${payload.joinToString(" ") { "%02X".format(it) }}")

                // Try to find the start of ASCII characters (skip non-printable bytes)
                var startIndex = 0
                for (i in payload.indices) {
                    val byte = payload[i].toInt() and 0xFF
                    // ASCII printable range: 0x20-0x7E
                    if (byte in 0x20..0x7E) {
                        startIndex = i
                        break
                    }
                }

                // Extract string from first printable character
                val cleanPayload = payload.copyOfRange(startIndex, payload.size)
                val serialNumber = String(cleanPayload, Charsets.UTF_8)
                    .trim()
                    .trim('\u0000')
                    .filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".-_" }

                Log.e("GetSerialNumberCommand", "Serial Number: $serialNumber")
                Result.success(serialNumber)
            } else {
                Result.failure(Exception("Invalid response length"))
            }
        } catch (e: Exception) {
            Log.e("GetSerialNumberCommand", "Parse error: ${e.message}")
            Result.failure(e)
        }
    }
}
