package com.konami.ailens.ble.command

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.konami.ailens.ble.CRC32
import com.konami.ailens.ble.DeviceSession
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DrawTextBLECommand(
    session: DeviceSession,
    private val text: String,
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int
) : BaseBLECommand(session) {

    override fun getData() = buildPacket()

    // ---------- Build whole packet ----------
    private fun buildPacket(): ByteArray {
        val payload = buildTextPayload()

        // commDataHead: [0x50][len(u32)][crc2(u32)][reserved(16)]
        val commDataHead = ArrayList<Byte>(25)
        commDataHead.add(0x50) // COMM_DATA_ALGO_DRAW (bin type)
        commDataHead += u32(payload.size)
        commDataHead += u32(CRC32.calculate(payload, 0))
        repeat(16) { commDataHead.add(0x00) }

        // crc1 over (commDataHead + payload)
        val tmp = ByteArray(commDataHead.size + payload.size)
        for (i in commDataHead.indices) tmp[i] = commDataHead[i]
        System.arraycopy(payload, 0, tmp, commDataHead.size, payload.size)
        val crc1 = u32(CRC32.calculate(tmp, 0))

        // requestHead: [0x50][0x00 begin][crc1][index=0]
        val requestHead = ArrayList<Byte>(10)
        requestHead.add(0x50)
        requestHead.add(0x00) // single packet => BEGIN
        requestHead += crc1
        requestHead += u32(0) // index

        val totalLen = (requestHead.size + commDataHead.size + payload.size).toUShort()

        // cmdHead: 'ME' + CMD_BINARY_PACKET + len + len*2
        val cmdHead = ArrayList<Byte>(8)
        cmdHead.add(0x45); cmdHead.add(0x4D)           // 'ME'
        cmdHead.add(0x1A); cmdHead.add(0x00)           // CMD_BINARY_PACKET
        cmdHead += u16(totalLen.toInt())
        cmdHead += u16((totalLen * 2u).toInt())

        // concat all
        val out = ByteArray(cmdHead.size + requestHead.size + commDataHead.size + payload.size)
        var off = 0
        fun put(bytes: List<Byte>) { for (b in bytes) out[off++] = b }
        put(cmdHead); put(requestHead); put(commDataHead)
        System.arraycopy(payload, 0, out, off, payload.size)
        return out
    }

    // ---------- Build text TLV payload (0x60) ----------
    private fun buildTextPayload(): ByteArray {
        val body = ArrayList<Byte>()
        body.add(0x01)                          // type: text
        body += u16(0)                           // textSize 置 0（未知映射，先給 0）
        body += u16(x); body += u16(y)
        body += u16(width); body += u16(height)
        body += text.encodeToByteArray().toList()

        // TLV: [0x60][len(u16)] + body
        val payload = ArrayList<Byte>()
        payload.add(0x60)                        // PL_ALGO_DRAW_CANVAS
        payload += u16(body.size)
        payload += body
        return payload.toByteArray()
    }

    // ---------- helpers ----------
    private fun u16(v: Int): List<Byte> {
        val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(v.coerceIn(0, 0xFFFF).toShort())
        return bb.array().toList()
    }

    private fun u32(v: Int): List<Byte> {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(v)
        return bb.array().toList()
    }

//    private fun crc32(data: ByteArray): Int {
//        val c = CRC32()
//        c.update(data)
//        return (c.value and 0xFFFFFFFFL).toInt()
//    }
}