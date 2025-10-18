package com.konami.ailens.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Type-Length-Value structure for binary packet payloads
 * Encoded as: Type(1 byte) + Length(2 bytes, LE) + Value
 */
data class TLV(
    val type: UByte,
    val value: ByteArray
) {
    /**
     * Encodes this TLV as bytes: [type(1)][length(2, LE)][value]
     */
    fun toBytes(): ByteArray {
        val length = value.size
        val buffer = ByteBuffer.allocate(3 + length).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(type.toByte())
        buffer.putShort(length.toShort())
        buffer.put(value)
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TLV

        if (type != other.type) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }

    companion object {
        /**
         * Concatenate multiple TLVs into a single byte array
         */
        fun concat(tlvs: List<TLV>): ByteArray {
            val totalSize = tlvs.sumOf { it.toBytes().size }
            val buffer = ByteBuffer.allocate(totalSize)
            tlvs.forEach { buffer.put(it.toBytes()) }
            return buffer.array()
        }
    }
}
