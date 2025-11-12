package com.konami.ailens.ble.command

import com.konami.ailens.ble.CRC32
import com.konami.ailens.ble.Glasses

class ClearCanvasCommand(
    val x: UShort = 0u,
    val y: UShort = 0u,
    val width: UShort = 640u,
    val height: UShort = 480u,
) : VoidCommand() {

    fun getData(): ByteArray {
        val payload = cleanPayload()
        val crc2 = uint32ToBytes(CRC32.calculate(payload, 0))

        // Build commDataHead
        val commDataHead = mutableListOf<Byte>()
        commDataHead.addAll(crc2.toList())

        // reserved1 ~ reserved4: 4 * uint32 = 16 bytes of 0x00
        repeat(16) {
            commDataHead.add(0x00)
        }

        val headLengthBytes = uint32ToBytes(payload.size)
        commDataHead.addAll(0, headLengthBytes.toList())
        commDataHead.add(0, 0x50) // COMM_DATA_ALGO_DRAW

        // Calculate CRC1
        val crc1Input = commDataHead.toByteArray() + payload
        val crc1 = uint32ToBytes(CRC32.calculate(crc1Input, 0))

        // Build requestHead
        val requestHead = mutableListOf<Byte>()
        requestHead.add(0x50) // COMM_DATA_ALGO_DRAW
        requestHead.add(0x00) // PACKET_BEGIN
        requestHead.addAll(crc1.toList())

        // index (uint32) = 0x00000000
        repeat(4) {
            requestHead.add(0x00)
        }

        // Build cmdHead
        val length = (requestHead.size + commDataHead.size + payload.size).toUShort()
        val cmdHead = mutableListOf<Byte>()
        cmdHead.addAll(listOf(0x45, 0x4D)) // EM
        cmdHead.addAll(listOf(0x1A, 0x00)) // CMD_BINARY_PACKET
        cmdHead.addAll(uint16ToBytes(length).toList())
        cmdHead.addAll(uint16ToBytes((length * 2u).toUShort()).toList())

        // Combine all parts
        return cmdHead.toByteArray() + requestHead.toByteArray() + commDataHead.toByteArray() + payload
    }

    private fun cleanPayload(): ByteArray {
        val payload = mutableListOf<Byte>()
        payload.add(0x05) // type: clean
        payload.addAll(uint16ToBytes(x).toList())
        payload.addAll(uint16ToBytes(y).toList())
        payload.addAll(uint16ToBytes(width).toList())
        payload.addAll(uint16ToBytes(height).toList())

        val length = uint16ToBytes(payload.size.toUShort())

        val result = mutableListOf<Byte>()
        result.add(0x60) // PL_ALGO_DRAW_CANVAS
        result.addAll(length.toList())
        result.addAll(payload)

        return result.toByteArray()
    }

    // Helper function: UInt16 to Little-Endian ByteArray
    private fun uint16ToBytes(value: UShort): ByteArray {
        val uintValue = value.toUInt()
        return byteArrayOf(
            (uintValue and 0xFFu).toByte(),
            (uintValue shr 8 and 0xFFu).toByte()
        )
    }

    // Helper function: UInt32(Int) to Little-Endian ByteArray
    private fun uint32ToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    override fun execute(session: Glasses) {
        session.sendRaw(getData())
    }
}

