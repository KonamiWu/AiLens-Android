package com.konami.ailens.ble.command

import android.util.Log
import com.konami.ailens.ble.Glasses

/**
 * Get Bluetooth MAC address command
 * Reference iOS: CMD_GET_BT_ADDRESS = 0x19
 */
class GetMacAddressCommand : BLECommand<String>() {

    /**
     * Command format: 0x45 0x4D [cmd] 0x00 0x00 0x00 0x00 0x00
     * cmd = 0x19 for GET_BT_ADDRESS
     */
    private fun getData() = byteArrayOf(
        0x45.toByte(), 0x4D.toByte(), // Header
        0x19.toByte(),                // CMD_GET_BT_ADDRESS
        0x00.toByte(), 0x00.toByte(), // Data length (0)
        0x00.toByte(), 0x00.toByte(), 0x00.toByte()  // Padding
    )

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }

    override fun parseResult(data: ByteArray): Result<String> {
        return try {
            // Response format: 0x4F 0x42 [cmd] [len_low] [len_high] [data...]
            // data bytes contain 6-byte MAC address
            if (data.size >= 11) {  // Header(5) + MAC(6)
                val macBytes = data.copyOfRange(5, 11)
                val mac = macBytes.joinToString(":") { "%02X".format(it) }
                Log.e("GetMacAddressCommand", "MAC: $mac")
                Result.success(mac)
            } else {
                Result.failure(Exception("Invalid response length"))
            }
        } catch (e: Exception) {
            Log.e("GetMacAddressCommand", "Parse error: ${e.message}")
            Result.failure(e)
        }
    }
}
