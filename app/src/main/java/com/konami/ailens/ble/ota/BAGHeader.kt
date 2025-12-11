package com.konami.ailens.ble.ota

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BAG File Header
 * Reference iOS: XRGlassesBAGHeader
 * Size: 32 bytes
 */
data class BAGHeader(
    val magic: UInt,        // 0x4741424d "GABM"
    val version: UInt,
    val devType: UInt,
    val timestamp: UInt,
    val length: UInt,
    val resvd1: UInt,
    val resvd2: UInt,
    val resvd3: UInt
) {
    val isValid: Boolean
        get() = magic == MAGIC

    companion object {
        const val SIZE = 32
        const val MAGIC = 0x4741424Du  // "GABM"

        fun fromBytes(data: ByteArray): BAGHeader? {
            if (data.size < SIZE) return null

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            return BAGHeader(
                magic = buffer.int.toUInt(),
                version = buffer.int.toUInt(),
                devType = buffer.int.toUInt(),
                timestamp = buffer.int.toUInt(),
                length = buffer.int.toUInt(),
                resvd1 = buffer.int.toUInt(),
                resvd2 = buffer.int.toUInt(),
                resvd3 = buffer.int.toUInt()
            )
        }
    }
}
