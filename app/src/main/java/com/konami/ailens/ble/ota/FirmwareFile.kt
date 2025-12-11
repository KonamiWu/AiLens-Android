package com.konami.ailens.ble.ota

import android.util.Log
import com.konami.ailens.ble.CRC32
import java.io.File

/**
 * Firmware BAG file parser
 * Reference iOS: XRGlassesOTAService checkFile method
 */
class FirmwareFile(
    val bagHeader: BAGHeader,
    val sections: List<FirmwareSection>
) {
    val totalDataLength: UInt
        get() = sections.sumOf { (it.header.fwLength + OTAHeader.SIZE.toUInt()).toLong() }.toUInt()

    data class FirmwareSection(
        val header: OTAHeader,
        val data: ByteArray
    ) {
        val fullData: ByteArray
            get() {
                val result = ByteArray(OTAHeader.SIZE + data.size)
                // OTA header + firmware data
                System.arraycopy(header.toBytes(), 0, result, 0, OTAHeader.SIZE)
                System.arraycopy(data, 0, result, OTAHeader.SIZE, data.size)
                return result
            }
    }

    companion object {
        private const val TAG = "FirmwareFile"

        fun parse(file: File): Result<FirmwareFile> {
            return try {
                val fileData = file.readBytes()

                // 1. Parse BAG header
                val bagHeader = BAGHeader.fromBytes(fileData)
                    ?: return Result.failure(Exception("Invalid BAG header"))

                if (!bagHeader.isValid) {
                    return Result.failure(Exception("Invalid BAG magic: 0x${bagHeader.magic.toString(16)}"))
                }

                Log.e(TAG, "BAG Header: version=${bagHeader.version}, devType=${bagHeader.devType}, length=${bagHeader.length}")

                // 2. Parse OTA sections
                val sections = mutableListOf<FirmwareSection>()
                var offset = BAGHeader.SIZE
                var totalParsedLength = 0u

                while (offset < fileData.size && offset + OTAHeader.SIZE <= fileData.size) {
                    val headerData = fileData.copyOfRange(offset, offset + OTAHeader.SIZE)
                    val otaHeader = OTAHeader.fromBytes(headerData)
                        ?: break

                    if (!otaHeader.isValid) {
                        Log.e(TAG, "Invalid OTA magic at offset $offset: 0x${otaHeader.magic.toString(16)}")
                        break
                    }

                    Log.e(TAG, "OTA Section: version=${otaHeader.version}, type=${otaHeader.fwDataType}, length=${otaHeader.fwLength}")

                    // Read firmware data
                    val dataStart = offset + OTAHeader.SIZE
                    val dataEnd = dataStart + otaHeader.fwLength.toInt()

                    if (dataEnd > fileData.size) {
                        return Result.failure(Exception("Firmware data exceeds file size"))
                    }

                    val firmwareData = fileData.copyOfRange(dataStart, dataEnd)

                    // 3. Verify CRC32
                    val calculatedCrc = CRC32.calculate(firmwareData, 0)
                    if (calculatedCrc.toUInt() != otaHeader.fwCrc) {
                        return Result.failure(
                            Exception("CRC mismatch for section ${otaHeader.fwDataType}: " +
                                "expected=${otaHeader.fwCrc.toString(16)}, " +
                                "got=${calculatedCrc.toUInt().toString(16)}")
                        )
                    }

                    sections.add(FirmwareSection(otaHeader, firmwareData))
                    totalParsedLength += (OTAHeader.SIZE.toUInt() + otaHeader.fwLength)
                    offset = dataEnd
                }

                // 4. Verify total length
                if (totalParsedLength != bagHeader.length) {
                    return Result.failure(
                        Exception("Total length mismatch: expected=${bagHeader.length}, got=$totalParsedLength")
                    )
                }

                Log.e(TAG, "Successfully parsed ${sections.size} firmware sections")
                Result.success(FirmwareFile(bagHeader, sections))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse firmware file: ${e.message}")
                Result.failure(e)
            }
        }

        /**
         * Filter sections that need update based on device version
         */
        fun filterUpdateSections(
            firmwareFile: FirmwareFile,
            deviceVersions: Map<UInt, String>,
            forceUpdate: Boolean = false
        ): List<FirmwareSection> {
            return firmwareFile.sections.filter { section ->
                val header = section.header
                val deviceVersion = deviceVersions[header.fwDataType] ?: "0"
                val needUpdate = compareVersion(deviceVersion, header.version.toString()) < 0

                needUpdate || header.forceUpdate > 0u || forceUpdate
            }
        }

        private fun compareVersion(current: String, target: String): Int {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val targetParts = target.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(currentParts.size, targetParts.size)
            for (i in 0 until maxLength) {
                val c = currentParts.getOrNull(i) ?: 0
                val t = targetParts.getOrNull(i) ?: 0
                when {
                    c < t -> return -1
                    c > t -> return 1
                }
            }
            return 0
        }
    }
}

private fun OTAHeader.toBytes(): ByteArray {
    val buffer = java.nio.ByteBuffer.allocate(OTAHeader.SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(magic.toInt())
    buffer.putInt(fwStartAddr.toInt())
    buffer.putInt(fwLength.toInt())
    buffer.putInt(fwCrc.toInt())
    buffer.putInt(secInfoLen.toInt())
    buffer.putInt(fwMaxSize.toInt())
    buffer.putInt(forceUpdate.toInt())
    buffer.putInt(reserved1.toInt())    // iOS: resvd3
    buffer.putInt(version.toInt())
    buffer.putInt(fwDataType.toInt())
    buffer.putInt(storageType.toInt())
    buffer.putInt(imageId.toInt())
    return buffer.array()
}
