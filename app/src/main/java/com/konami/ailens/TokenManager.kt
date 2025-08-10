package com.konami.ailens

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.content.edit

class TokenManager {

    private val fixBytes = byteArrayOf(
        0x10, 0x01, 0xEB.toByte(), 0xE9.toByte(), 0xBE.toByte(), 0xF7.toByte(), 0xFA.toByte(), 0x4B,
        0x47, 0x74, 0xA2.toByte(), 0xF6.toByte(), 0xB3.toByte(), 0x2A, 0x79, 0xC5.toByte(),
        0xF2.toByte(), 0x73
    )

    fun createConnectionToken(userId: UInt, cloudToken: UInt): ByteArray {
        val token = mutableListOf<Byte>()
        val thirds = getTokenPrefix(cloudToken)
        token.addAll(thirds.toList())
        token.add(0xE0.toByte())
        token.addAll(userId.toLittleEndianBytes().toList())
        token.addAll(fixBytes.toList())
        val crc8 = crc8Maxim(token.toByteArray())
        token.add(crc8)
        return token.toByteArray()
    }

    fun getRetrieveToken(mac: String, userId: UInt, deviceToken: ByteArray) : ByteArray{
        val retrieveToken = mutableListOf<Byte>()
        retrieveToken.addAll(deviceToken.toList())
        retrieveToken.addAll(userId.toLittleEndianBytes().toList())
        retrieveToken.addAll(fixBytes.toList())
        val crc8 = crc8Maxim(retrieveToken.toByteArray())
        retrieveToken.add(crc8)

        return retrieveToken.toByteArray()
    }

    private fun getTokenPrefix(cloudToken: UInt): ByteArray {
        val timeBits = cloudToken and 0x00FFFFFFu
        val byte0 = (timeBits and 0xFFu).toByte()
        val byte1 = ((timeBits shr 8) and 0xFFu).toByte()
        val byte2 = ((timeBits shr 16) and 0xFFu).toByte()
        return byteArrayOf(byte0, byte1, byte2)
    }

    private fun UInt.toLittleEndianBytes(): ByteArray =
        byteArrayOf(
            (this and 0xFFu).toByte(),
            ((this shr 8) and 0xFFu).toByte(),
            ((this shr 16) and 0xFFu).toByte(),
            ((this shr 24) and 0xFFu).toByte()
        )

    /*
    CRC8 Maxim calculation converted for Kotlin,
    polynomial 0x31, starting with 0x00 initial_crc
     */
    private fun crc8Maxim(data: ByteArray): Byte {
        var crc = 0x00
        for (b in data) {
            crc = (crc xor (b.toInt() and 0xFF)) and 0xFF
            for (i in 0 until 8) {
                crc = if ((crc and 0x80) != 0) {
                    ((crc shl 1) xor 0x31) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc.toByte()
    }

    // Helper extensions for hex conversions if desired for storage
    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexStringToByteArray(): ByteArray {
        val len = this.length
        val result = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            result[i / 2] = ((this[i].toDigit() shl 4) + this[i + 1].toDigit()).toByte()
            i += 2
        }
        return result
    }

    private fun Char.toDigit(): Int {
        return this.digitToInt(16)
    }
}