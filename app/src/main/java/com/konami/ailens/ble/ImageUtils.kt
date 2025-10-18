package com.konami.ailens.ble

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.ceil

/**
 * Image processing utilities for converting images to grayscale and packing for BLE transmission
 */
object ImageUtils {

    enum class Depth {
        L4,  // 4-bit per pixel
        L8   // 8-bit per pixel
    }

    data class GrayImage(
        val pixels: ByteArray,
        val width: Int,
        val height: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GrayImage

            if (!pixels.contentEquals(other.pixels)) return false
            if (width != other.width) return false
            if (height != other.height) return false

            return true
        }

        override fun hashCode(): Int {
            var result = pixels.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }

    /**
     * Convert bitmap to 8-bit grayscale
     * Formula: Gray = 0.3*R + 0.59*G + 0.11*B
     *
     * @param bitmap Input bitmap
     * @return GrayImage with 8-bit grayscale pixels
     */
    fun toGray8(bitmap: Bitmap): GrayImage {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = ByteArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            // Apply grayscale formula
            val v = (r * 0.3 + g * 0.59 + b * 0.11).toInt()
            gray[i] = v.coerceIn(0, 255).toByte()
        }

        return GrayImage(gray, width, height)
    }

    /**
     * Pack Gray8 to L4 format (4 bits per pixel)
     * Two pixels packed into one byte: high nibble from first pixel, low nibble from second
     *
     * @param gray8 8-bit grayscale pixel array
     * @return L4 packed byte array
     */
    fun packL4(gray8: ByteArray): ByteArray {
        val out = ByteArray((gray8.size + 1) / 2)
        var i = 0
        var outIdx = 0

        while (i + 1 < gray8.size) {
            val hi = (ceil((gray8[i].toInt() and 0xFF) / 16.0).toInt() and 0x0F).toByte()
            val lo = (gray8[i + 1].toInt() and 0x0F).toByte()
            out[outIdx] = ((hi.toInt() shl 4) or lo.toInt()).toByte()
            i += 2
            outIdx++
        }

        // Handle odd pixel count
        if (i < gray8.size) {
            val hi = (ceil((gray8[i].toInt() and 0xFF) / 16.0).toInt() and 0x0F).toByte()
            out[outIdx] = (hi.toInt() shl 4).toByte()
        }

        return out
    }

    /**
     * Pack Gray8 to L8 format (8 bits per pixel)
     * This is essentially a no-op, just returns the original array
     *
     * @param gray8 8-bit grayscale pixel array
     * @return L8 packed byte array (same as input)
     */
    fun packL8(gray8: ByteArray): ByteArray {
        return gray8
    }

    /**
     * Convert bitmap to packed format (L4 or L8)
     *
     * @param bitmap Input bitmap
     * @param depth Desired depth (L4 or L8)
     * @return Pair of (packed bytes, GrayImage info)
     */
    fun bitmapToPacked(bitmap: Bitmap, depth: Depth): Pair<ByteArray, GrayImage> {
        val gray = toGray8(bitmap)
        val packed = when (depth) {
            Depth.L4 -> packL4(gray.pixels)
            Depth.L8 -> packL8(gray.pixels)
        }
        return Pair(packed, gray)
    }
}
