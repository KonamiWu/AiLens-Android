package com.konami.ailens.ble.ota

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * OTA Header
 * Reference iOS: XRGlassesOTAHeader
 * Size: 48 bytes
 */
data class OTAHeader(
    val magic: UInt,            // 0x5746424d "WFBM"
    val fwStartAddr: UInt,
    val fwLength: UInt,
    val fwCrc: UInt,
    val secInfoLen: UInt,
    val fwMaxSize: UInt,
    val forceUpdate: UInt,
    val reserved1: UInt,        // iOS: resvd3 (field 8)
    val version: UInt,          // iOS: field 9
    val fwDataType: UInt,       // iOS: field 10 - bin type
    val storageType: UInt,      // iOS: field 11
    val imageId: UInt           // iOS: field 12 (iOS has duplicate storageType)
) {
    val isValid: Boolean
        get() = magic == MAGIC

    companion object {
        const val SIZE = 48
        const val MAGIC = 0x5746424Du  // "WFBM"

        fun fromBytes(data: ByteArray): OTAHeader? {
            if (data.size < SIZE) return null

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            return OTAHeader(
                magic = buffer.int.toUInt(),
                fwStartAddr = buffer.int.toUInt(),
                fwLength = buffer.int.toUInt(),
                fwCrc = buffer.int.toUInt(),
                secInfoLen = buffer.int.toUInt(),
                fwMaxSize = buffer.int.toUInt(),
                forceUpdate = buffer.int.toUInt(),
                reserved1 = buffer.int.toUInt(),    // iOS: resvd3
                version = buffer.int.toUInt(),
                fwDataType = buffer.int.toUInt(),
                storageType = buffer.int.toUInt(),
                imageId = buffer.int.toUInt()
            )
        }
    }
}
