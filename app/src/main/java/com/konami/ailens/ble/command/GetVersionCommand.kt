package com.konami.ailens.ble.command

import android.util.Log
import com.konami.ailens.ble.Glasses

/**
 * Get firmware version command
 * Reference iOS: CMD_GET_VERSION = 0x02
 * XRGlassesOTAService.m:110-123
 */
class GetVersionCommand : BLECommand<String>() {

    /**
     * Command format: 0x45 0x4D [cmd] 0x00 0x00 0x00 0x00 0x00
     * cmd = 0x02 for GET_VERSION
     */
    private fun getData() = byteArrayOf(
        0x45.toByte(), 0x4D.toByte(), // Header
        0x02.toByte(),                // CMD_GET_VERSION
        0x00.toByte(), 0x00.toByte(), // Data length (0)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte()  // Padding
    )

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    override fun parseResult(data: ByteArray): Result<String> {
        return try {
            // Response format: 0x4F 0x42 [cmd] [len_low] [len_high] [data...]
            // data bytes contain version info
            if (data.size > 5) {
                val payload = data.copyOfRange(5, data.size)
                val version = parseVersion(payload)
                Log.e("GetVersionCommand", "Version: $version")
                Result.success(version)
            } else {
                Result.failure(Exception("Invalid response length"))
            }
        } catch (e: Exception) {
            Log.e("GetVersionCommand", "Parse error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Parse version from payload
     * Reference iOS: XRDeviceVersion
     */
    private fun parseVersion(payload: ByteArray): String {
        if (payload.size < 3) {
            return "Unknown"
        }

        // Version format: major.minor.patch
        val major = payload[0].toInt() and 0xFF
        val minor = payload[1].toInt() and 0xFF
        val patch = payload[2].toInt() and 0xFF

        return "$major.$minor.$patch"
    }
}
