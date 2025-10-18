package com.konami.ailens.ble

/**
 * Run-Length Compression (RLC) implementation
 *
 * Encoding format:
 * - header = [len(7 bits) | tag(1 bit)]
 * - tag=0: repeat section (followed by 1 value)
 * - tag=1: single section (followed by len values)
 * - Maximum section length: 127
 *
 * This is a Kotlin port of the iOS Swift implementation which was ported from C.
 */
object RLC {
    private const val SECTION_MAX = 127

    /**
     * Compress byte array using RLC algorithm
     *
     * @param src Source byte array to compress
     * @return Compressed byte array, or empty array if compression fails
     */
    fun compress(src: ByteArray): ByteArray {
        if (src.size <= 1) return byteArrayOf()

        val out = mutableListOf<Byte>()
        var srcOffset = 0
        var repeatByteCnt = 0
        var totalBytes = 0

        fun writeSection(bytes: Int): Boolean {
            if (bytes <= 0 || bytes > SECTION_MAX) return false

            val isRepeat = if (bytes >= 2) {
                src[srcOffset] == src[srcOffset + 1]
            } else {
                false
            }

            val tag: UByte = if (isRepeat) 0u else 1u
            val len = (bytes and 0x7F).toUByte()
            val header = ((tag.toInt() shl 7) or len.toInt()).toByte()
            out.add(header)

            if (isRepeat) {
                out.add(src[srcOffset])
            } else {
                for (i in 0 until bytes) {
                    out.add(src[srcOffset + i])
                }
            }

            srcOffset += bytes
            totalBytes += bytes
            return true
        }

        var pos = 0
        while (pos < src.size) {
            if (pos == 0) {
                pos++
                continue
            }

            if (src[pos] == src[pos - 1]) {
                repeatByteCnt++
                if (repeatByteCnt == 1 && srcOffset < (pos - 1)) {
                    val bytes = (pos - 1) - srcOffset
                    if (!writeSection(bytes)) return byteArrayOf()
                }
            } else {
                if (repeatByteCnt != 0 && srcOffset < (pos - 1)) {
                    val bytes = (pos - 1) - srcOffset + 1
                    if (!writeSection(bytes)) return byteArrayOf()
                    repeatByteCnt = 0
                }
            }

            val bytesNow = pos - srcOffset + 1
            if (bytesNow == SECTION_MAX) {
                if (!writeSection(bytesNow)) return byteArrayOf()
            }

            if (pos == src.size - 1 && totalBytes < src.size) {
                val remain = src.size - totalBytes
                if (!writeSection(remain)) return byteArrayOf()
            }

            pos++
        }

        return if (totalBytes == src.size) {
            out.toByteArray()
        } else {
            byteArrayOf()
        }
    }

    /**
     * Decompress RLC-encoded byte array
     *
     * @param src Compressed source byte array
     * @param dstSize Expected size of decompressed output
     * @return Decompressed byte array, or null if decompression fails
     */
    fun uncompress(src: ByteArray, dstSize: Int): ByteArray? {
        if (src.size <= 1 || dstSize <= 0) return null

        val out = ByteArray(dstSize)
        var so = 0  // source offset
        var dof = 0 // destination offset

        while (so < src.size) {
            val header = src[so].toInt() and 0xFF
            so++

            val tag = (header shr 7) and 0x1
            val len = header and 0x7F

            if (len > SECTION_MAX || dof + len > dstSize) return null

            if (tag == 0) {  // Repeat section
                if (so >= src.size) return null
                val v = src[so]
                so++
                for (i in 0 until len) {
                    out[dof + i] = v
                }
                dof += len
            } else {  // Single section
                if (so + len > src.size) return null
                System.arraycopy(src, so, out, dof, len)
                so += len
                dof += len
            }
        }

        return if (dof == dstSize) out else null
    }
}
