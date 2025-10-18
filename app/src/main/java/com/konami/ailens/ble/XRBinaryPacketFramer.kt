package com.konami.ailens.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary packet framer that builds frames with slicing for BLE transmission
 *
 * Packet structure:
 * - meta_cmd_head (8B): magic(45 4D) | type(1A 00) | valueLen | valueLen*2
 * - meta_packet_req_head (10B): [bin, pktType, crc32(subPayload), index(u32)]
 * - subPayload = meta_comm_data_head(25B) + inner
 *
 * meta_comm_data_head: [bin(1)][len(u32)][crc(u32)][reserved1..4(u32)]
 */
class XRBinaryPacketFramer(
    private val crc32: CRC32 = CRC32
) {

    enum class PacketSliceType(val value: UByte) {
        BEGIN(0x00u),
        MIDDLE(0x01u),
        END(0x02u)
    }

    companion object {
        private val MAGIC_LE = byteArrayOf(0x45, 0x4D)  // 'ME' little-endian
        private val OUTER_CMD_TYPE_LE = byteArrayOf(0x1A, 0x00)  // CMD_BINARY_PACKET (0x001A)
        private const val DUPLICATE_LEN_TIMES_2 = true
    }

    /**
     * Build sliced frames for BLE transmission
     *
     * @param binType Binary type (e.g., 0x33 for navigation, 0x50 for drawing, 0x67 for simultaneous translation)
     * @param inner Inner payload bytes (can be TLV concatenation or raw bytes)
     * @param mtu Maximum transmission unit from peripheral.maximumWriteValueLength
     * @param reservedOverride Optional override for 4 reserved u32 values in common_data (default zeros)
     * @return List of frames ready for BLE transmission
     */
    fun buildFrames(
        binType: UByte,
        inner: ByteArray,
        mtu: Int,
        reservedOverride: List<UInt>? = null
    ): List<ByteArray> {
        require(mtu > 18) { "MTU too small: must be > 18 (8 cmd head + 10 packet head)" }

        // Build common_data_head: bin(1) + len(u32) + crc(u32) + reserved1..4(u32)
        val commHead = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(binType.toByte())
            putInt(inner.size)
            putInt(crc32.calculate(inner, 0))

            // Reserved fields (4 x u32)
            if (reservedOverride != null && reservedOverride.size == 4) {
                reservedOverride.forEach { putInt(it.toInt()) }
            } else {
                repeat(4) { putInt(0) }
            }
        }.array()

        // subPayload to be sliced = commHead + inner
        val commonData = commHead + inner

        // Calculate max sub-payload size per frame
        val maxSub = maxOf(1, mtu - 8 - 10)  // 8 = cmd head, 10 = packet head

        val frames = mutableListOf<ByteArray>()
        var index = 0u
        var offset = 0

        while (offset < commonData.size) {
            val end = minOf(offset + maxSub, commonData.size)
            val sub = commonData.copyOfRange(offset, end)
            val isFirst = (offset == 0)
            val isLast = (end == commonData.size)

            // Build meta_packet_req_head (10B): [bin, pktType, crc32(sub), index]
            val pktType = when {
                isFirst && isLast -> PacketSliceType.BEGIN.value  // Single packet
                isFirst -> PacketSliceType.BEGIN.value
                isLast -> PacketSliceType.END.value
                else -> PacketSliceType.MIDDLE.value
            }

            val pkt = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(binType.toByte())
                put(pktType.toByte())
                putInt(crc32.calculate(sub, 0))
                putInt(index.toInt())
            }.array()

            // Build meta_cmd_head (8B): magic | type | len | (len or len*2)
            val valueLen = (pkt.size + sub.size).toUShort()
            val cmd = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(MAGIC_LE)
                put(OUTER_CMD_TYPE_LE)
                putShort(valueLen.toShort())
                putShort(if (DUPLICATE_LEN_TIMES_2) (valueLen * 2u).toShort() else valueLen.toShort())
            }.array()

            // Concatenate: cmd + pkt + sub
            frames.add(cmd + pkt + sub)

            offset = end
            index++
        }

        return frames
    }
}
