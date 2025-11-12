package com.konami.ailens.ble.command

import com.konami.ailens.ble.CRC32
import com.konami.ailens.ble.Glasses

class DrawRectCommand(
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int,
    private val lineWidth: Int,
    private val fill: Boolean
) : VoidCommand() {

    private fun getData(): ByteArray {
        // ---- payload (draw body) ----
        val payload = rectPayload()
        val crc2 = uint32ToBytes(CRC32.calculate(payload, 0))

        // ---- meta_comm_data_head ----
        val commDataHead = mutableListOf<Byte>().apply {
            add(0x50.toByte()) // bin type: COMM_DATA_ALGO_DRAW
            addAll(uint32ToBytes(payload.size).toList()) // payload length (u32, LE)
            addAll(crc2.toList())                        // crc2 (u32, LE)
            repeat(16) { add(0x00) }                    // reserved[4] (4 * u32)
        }

        // ---- meta_packet_req_head ----
        val crc1Input = commDataHead.toByteArray() + payload
        val crc1 = uint32ToBytes(CRC32.calculate(crc1Input, 0))
        val reqHead = mutableListOf<Byte>().apply {
            add(0x50.toByte())         // bin type
            add(0x00.toByte())         // packet type: PACKET_BEGIN
            addAll(crc1.toList())      // crc1
            repeat(4) { add(0x00) }    // index (u32)
        }

        // ---- meta_cmd_head ----
        val totalLen = reqHead.size + commDataHead.size + payload.size
        val cmdHead = mutableListOf<Byte>().apply {
            add(0x45) ; add(0x4D)                 // 'E','M'
            add(0x1A) ; add(0x00)                 // type = CMD_BINARY_PACKET
            addAll(uint16ToBytes(totalLen).toList())          // total length (u16, LE)
            addAll(uint16ToBytes(totalLen * 2).toList())      // value length + pack_type（依你規格：兩倍）
        }

        // ---- concat ----
        return cmdHead.toByteArray() +
                reqHead.toByteArray() +
                commDataHead.toByteArray() +
                payload
    }

    private fun rectPayload(): ByteArray {
        val body = mutableListOf<Byte>().apply {
            add(0x02.toByte()) // type: rectangle
            addAll(uint16ToBytes(lineWidth.coerceIn(1, 0xFFFF)).toList())
//            addAll(uint16ToBytes(x.coerceIn(0, 0xFFFF)).toList())
//            addAll(uint16ToBytes(y.coerceIn(0, 0xFFFF)).toList())
            addAll(uint16ToBytes(x).toList())
            addAll(uint16ToBytes(y).toList())
            addAll(uint16ToBytes(width.coerceIn(1, 0xFFFF)).toList())
            addAll(uint16ToBytes(height.coerceIn(1, 0xFFFF)).toList())
            add(if (fill) 0x01 else 0x00)
        }

        val payload = mutableListOf<Byte>()
        payload.add(0x60) // draw payload tag
        payload.addAll(uint16ToBytes(body.size).toList()) // body length (u16, LE)
        payload.addAll(body)

        return payload.toByteArray()
    }

    // helpers
    private fun uint16ToBytes(value: Int): ByteArray {
        val v = value and 0xFFFF
        return byteArrayOf(
            (v and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte()
        )
    }

    private fun uint32ToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}
