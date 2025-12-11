package com.konami.ailens.ble.ota

import android.util.Log
import com.konami.ailens.ble.CRC32
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CommonData V1 protocol for OTA firmware transfer
 * Reference iOS: XRCommonData (with reserveds.count == 1 - NO 25-byte header)
 *
 * Packet structure:
 * - meta_cmd_head (8B): magic(45 4D) | type(1A 00) | total_len(u16) | packet_type(1bit) + value_len(15bits)
 * - meta_packet_req_head (10B): [bin, pktType, crc32(subPayload), index(u32)]
 * - subPayload = firmware data slice (NO common_data_head for OTA)
 *
 * Note: OTA uses simple XRCommonData without 25-byte header
 */
class CommonDataFramer(
    private val binType: UByte,  // Binary type (low byte of fwDataType)
    private val data: ByteArray,
    private val mtu: Int = 512  // BLE MTU (entire packet size)
) {
    private val totalPackets: Int
    private val packets: List<ByteArray>

    init {
        // Android ATT Write Without Response max payload = (MTU - 3)
        // Effective transport size = (mtu - 3). From that, subtract cmd head (8) and packet head (10).
        val effectiveMtu = maxOf(20, mtu - 3) // safeguard minimum
        val maxSubPayload = maxOf(1, effectiveMtu - CMD_HEADER_SIZE - PACKET_REQ_HEADER_SIZE)

        // Calculate total packets needed (all packets have same maxSubPayload)
        totalPackets = (data.size + maxSubPayload - 1) / maxSubPayload

        packets = buildPackets()
        Log.e(TAG, "CommonData: totalPackets=$totalPackets, dataSize=${data.size}, mtu=$mtu, maxSubPayload=$maxSubPayload")
    }

    private fun buildPackets(): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        val effectiveMtu = maxOf(20, mtu - 3)
        val maxSubPayload = maxOf(1, effectiveMtu - CMD_HEADER_SIZE - PACKET_REQ_HEADER_SIZE)
        var offset = 0

        for (i in 0 until totalPackets) {
            // iOS XRPacketType: BEGIN=0, MIDDLE=1, END=2
            val packetType = when {
                i == 0 -> PacketType.BEGIN
                i == totalPackets - 1 -> PacketType.END
                else -> PacketType.MIDDLE
            }

            // All packets: just firmware data slices (NO common_data_head for OTA)
            val payloadSize = minOf(maxSubPayload, data.size - offset)
            val subPayload = data.copyOfRange(offset, offset + payloadSize)
            offset += payloadSize

            val packet = buildPacket(binType, packetType, subPayload, i)
            result.add(packet)

            // Debug: log first packet header
            if (i == 0 && packet.size >= 18) {
                val headerHex = packet.take(min(50, packet.size)).joinToString(" ") { "%02X".format(it) }
                Log.e(TAG, "First packet header: $headerHex")
                Log.e(TAG, "  meta_cmd_head (8B): ${packet.take(8).joinToString(" ") { "%02X".format(it) }}")
                Log.e(TAG, "  meta_packet_req_head (10B): ${packet.slice(8..17).joinToString(" ") { "%02X".format(it) }}")
                Log.e(TAG, "  subPayload starts: ${packet.drop(18).take(20).joinToString(" ") { "%02X".format(it) }}")
            }
        }

        return result
    }

    private fun min(a: Int, b: Int) = if (a < b) a else b


    private fun buildPacket(
        binType: UByte,
        packetType: PacketType,
        fullPayload: ByteArray,
        index: Int
    ): ByteArray {
        val totalSize = CMD_HEADER_SIZE + PACKET_REQ_HEADER_SIZE + fullPayload.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // meta_cmd_head (8B)
        buffer.putShort(MAGIC)  // 0x45, 0x4D ('EM' LE)
        buffer.putShort(CMD_BINARY_PACKET)  // 0x1A, 0x00
        val valueLen = (PACKET_REQ_HEADER_SIZE + fullPayload.size)
        buffer.putShort(valueLen.toShort())  // total_len

        // Bitfield: bit 0 = meta_cmd_packet_type (PACKET_BEGIN=0, PACKET_LEFT=1), bits 1-15 = value_len
        // CRITICAL: Each CommonData packet is sent as a separate complete command (0x1A),
        // so ALL packets use PACKET_BEGIN, not PACKET_LEFT. This is different from regular
        // multi-packet commands where subsequent packets use PACKET_LEFT.
        // Reference: iOS XRCommonDataManager.frameWithPayload wraps each packet independently
        val metaCmdPacketType = 0  // Always PACKET_BEGIN for CommonData
        val bitfield = (metaCmdPacketType or (valueLen shl 1)).toShort()
        buffer.putShort(bitfield)  // packet_type (1bit) + value_len (15bits)

        // meta_packet_req_head (10B)
        buffer.put(binType.toByte())
        buffer.put(packetType.value.toByte())

        // CRC32 of fullPayload (includes common_data_head for first packet)
        val crc = CRC32.calculate(fullPayload, 0)
        buffer.putInt(crc)

        // Packet index
        buffer.putInt(index)

        // fullPayload (with common_data_head for first packet, or just firmware data for others)
        buffer.put(fullPayload)

        return buffer.array()
    }

    /**
     * Get packet at specific index
     */
    fun getPacket(index: Int): ByteArray? {
        return packets.getOrNull(index)
    }

    /**
     * Get all packets
     */
    fun getAllPackets(): List<ByteArray> = packets

    /**
     * Get total packet count
     */
    fun getPacketCount(): Int = totalPackets

    enum class PacketType(val value: UByte) {
        BEGIN(0x00u),    // First packet (iOS: XRPacketTypeBegin)
        MIDDLE(0x01u),   // Middle packets (iOS: XRPacketTypeMiddle)
        END(0x02u)       // Last packet (iOS: XRPacketTypeEnd)
    }

    companion object {
        private const val TAG = "CommonDataFramer"

        // Protocol constants
        private const val CMD_HEADER_SIZE = 8
        private const val PACKET_REQ_HEADER_SIZE = 10

        private const val MAGIC: Short = 0x4D45  // 'EM' in little endian
        private const val CMD_BINARY_PACKET: Short = 0x001A
    }
}
