package com.konami.ailens.ble.command

import android.util.Log
import com.konami.ailens.ble.Glasses

/**
 * Get device type command
 * Reference iOS: CMD_GET_DEV_TYPE = 0x2a
 * XRGlassesOTAService.m:125-136
 */
class GetDeviceTypeCommand : BLECommand<String>() {

    /**
     * Command format: 0x45 0x4D [cmd] 0x00 0x00 0x00 0x00 0x00
     * cmd = 0x2a for GET_DEV_TYPE
     */
    private fun getData() = byteArrayOf(
        0x45.toByte(), 0x4D.toByte(), // Header
        0x2a.toByte(),                // CMD_GET_DEV_TYPE
        0x00.toByte(), 0x00.toByte(), // Data length (0)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte()  // Padding
    )

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    override fun parseResult(data: ByteArray): Result<String> {
        return try {
            // Response format: 0x4F 0x42 [cmd] [len_low] [len_high] [data...]
            if (data.size > 5) {
                val payload = data.copyOfRange(5, data.size - 1)  // Exclude last byte

                // Convert to hex string to extract model code
                val hexString = payload.joinToString("") { "%02X".format(it) }
                Log.e("GetDeviceTypeCommand", "Raw hex: $hexString")

                // Extract model code: format is "021500070702" or "02150000"
                // Remove "0215" prefix and take next 4 digits
                val modelCode = extractModelCode(hexString)

                Log.e("GetDeviceTypeCommand", "Model code: $modelCode")
                Result.success(modelCode)
            } else {
                Result.failure(Exception("Invalid response length"))
            }
        } catch (e: Exception) {
            Log.e("GetDeviceTypeCommand", "Parse error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Extract 4-digit model code from hex string
     * Format: "021500070702" → "0007"
     *         "02150000" → "0000"
     */
    private fun extractModelCode(hexString: String): String {
        // Look for "0215" prefix
        val prefixIndex = hexString.indexOf("0215")
        if (prefixIndex >= 0 && hexString.length >= prefixIndex + 8) {
            // Extract 4 characters after "0215"
            return hexString.substring(prefixIndex + 4, prefixIndex + 8)
        }

        // Fallback: if no "0215" prefix found, assume it's already the model code
        return if (hexString.length >= 4) {
            hexString.substring(0, 4)
        } else {
            "0000"  // Default to Bach
        }
    }
}
