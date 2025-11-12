package com.konami.ailens.ble.command

import com.konami.ailens.ble.Glasses

// MARK: - V1 meta_cmd framer (8B header)
object ChatGPTV1Framer {
    const val MAGIC: UShort = 0x4D45u

    private fun u16le(v: UShort): ByteArray {
        return byteArrayOf(
            (v.toInt() and 0xFF).toByte(),
            ((v.toInt() shr 8) and 0xFF).toByte()
        )
    }

    /**
     * Build final BLE frames for command (0x6F / 0x70), using V1 protocol.
     */
    fun buildFrames(cmd: UShort, sessionId: UByte, text: String, mtu: Int): List<ByteArray> {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        val maxPayload = mtu - 8 - 3  // 8 = meta_cmd header, 3 = [count, index, sessionId]
        val count = maxOf(1, (utf8.size + maxPayload - 1) / maxPayload).toUByte()

        val result = mutableListOf<ByteArray>()
        var offset = 0

        for (i in 1..count.toInt()) {
            val index = i.toUByte()
            val end = minOf(offset + maxPayload, utf8.size)
            val slice = utf8.sliceArray(offset until end)
            offset = end

            // Build value: [count, index, sessionId] + slice
            val value = byteArrayOf(count.toByte(), index.toByte(), sessionId.toByte()) + slice
            val valueLen = value.size.toUShort()
            val isFirst = (index.toInt() == 1)
            val packetBit: UShort = if (isFirst) 0u else 1u
            val bitfield: UShort = ((valueLen.toInt() shl 1) or packetBit.toInt()).toUShort()

            // Build frame
            val frame = u16le(MAGIC) +
                    u16le(cmd) +
                    u16le(valueLen) +
                    u16le(bitfield) +
                    value

            result.add(frame)
        }

        return result
    }
}

class TextToAgentCommand(
    private val sessionId: UByte,
    private val text: String
) : VoidCommand() {

    override fun execute(session: Glasses) {
        val mtu = session.mtu

        val frames = ChatGPTV1Framer.buildFrames(
            cmd = 0x70u,
            sessionId = sessionId,
            text = text,
            mtu = mtu
        )

        for (frame in frames) {
            session.add {
                session.sendRaw(frame)
            }
        }
    }
}