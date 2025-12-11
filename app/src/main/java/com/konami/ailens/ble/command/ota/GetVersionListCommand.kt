package com.konami.ailens.ble.command.ota

import android.util.Log
import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.BLECommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Get firmware version list from device
 * Command: 0x29 (CMD_GET_VERSION_LIST_29)
 * Reference iOS: XRGlassesOTAService
 */
class GetVersionListCommand : BLECommand<Map<UInt, String>>() {
    private fun getData() = byteArrayOf(0x45, 0x4D, 0x29, 0x00, 0x00, 0x00, 0x00, 0x00)

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    override fun parseResult(data: ByteArray): Result<Map<UInt, String>> {
        return try {
            // Android RX wraps frames with 0x4F 0x42 [cmd] [len_lo] [len_hi] then payload
            // Payload: [status(1B)] [version entries (4B each: major, minor, patch, type)]
            if (data.size < 7) {
                return Result.failure(Exception("Response too short: ${data.size} bytes"))
            }

            Log.e(TAG, "Raw response hex: ${data.joinToString(" ") { "%02X".format(it) }}")

            // Check status at payload[0]
            val status = data[5].toUByte()
            if (status != 0u.toUByte()) {
                return Result.failure(Exception("Command failed with status: 0x${status.toString(16)}"))
            }

            // Version data starts after 5-byte header and 1-byte status
            val versionDataStart = 9
            val versionData = data.copyOfRange(versionDataStart, data.size)

            Log.e(TAG, "Version data (${versionData.size} bytes): ${versionData.joinToString(" ") { "%02X".format(it) }}")

            val buffer = ByteBuffer.wrap(versionData).order(ByteOrder.LITTLE_ENDIAN)
            val versions = mutableMapOf<UInt, String>()
            var index = 0

            // Parse as 4-byte chunks: [major, minor, patch, type]
            // Example: 00 00 02 22 = major=0, minor=0, patch=2, type=0x22(34)
            while (buffer.remaining() >= 4) {
                val major = buffer.get().toUByte().toUInt()
                val minor = buffer.get().toUByte().toUInt()
                val patch = buffer.get().toUByte().toUInt()
                val type = buffer.get().toUByte().toUInt()

                val versionString = "$major.$minor.$patch"
                versions[type] = versionString

                Log.e(TAG, "Version[$index]: type=$type (0x${type.toString(16)}), version=$versionString")
                index++
            }

            Result.success(versions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse version list: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "GetVersionListCommand"
    }
}
