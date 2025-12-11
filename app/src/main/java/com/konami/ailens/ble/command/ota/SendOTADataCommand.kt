package com.konami.ailens.ble.command.ota

import android.util.Log
import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.BLECommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Send single OTA firmware data packet using CommonData protocol
 * Reference iOS: XRCommonData sendDataAtIndex + XRPacketResponse
 *
 * Response format (6 bytes):
 * - status (1B): 0x00 = success, 0x12 = error
 * - binType (1B)
 * - index (4B): packet index that device received
 */
data class OTAPacketResponse(
    val status: UByte,
    val binType: UByte,
    val index: UInt
)

class SendOTADataCommand(
    private val packet: ByteArray,
    private val packetIndex: Int,
    private val total: Int
) : BLECommand<OTAPacketResponse>() {

    override fun execute(session: Glasses) {
        session.sendRaw(packet)
        Log.e(TAG, "Sent OTA packet ${packetIndex + 1}/$total (${packet.size} bytes)")
    }

    override fun parseResult(data: ByteArray): Result<OTAPacketResponse> {
        return try {
            // Android RX format: 0x4F 0x42 [cmd] [len_lo] [len_hi] [payload...]
            // Payload for 0x1A response: [status(1B)] [binType(1B)] [index(4B LE)]
            if (data.size < 11) {
                return Result.failure(Exception("Response too short: ${data.size} bytes"))
            }

            val status = data[5].toUByte()
            val binType = data[6].toUByte()
            val indexBytes = data.copyOfRange(7, 11)
            val index = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN).int.toUInt()

            if (status != 0x00.toUByte()) {
                return Result.failure(Exception("Device returned error status: 0x${status.toString(16)}"))
            }


            Result.success(OTAPacketResponse(status, binType, index))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OTA response: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "SendOTADataCommand"
    }
}
