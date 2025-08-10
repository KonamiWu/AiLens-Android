package com.konami.ailens.facedetection

import android.graphics.*
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.apply
import kotlin.math.max
import kotlin.math.min
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.coerceAtMost
import kotlin.ranges.until

fun ImageProxy.toBitmap(): Bitmap {
    val nv21 = yuv420888ToNv21(this)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 95, out)
    val bytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    // VU order for NV21
    val chromaRowStride = image.planes[1].rowStride
    val chromaRowPadding = chromaRowStride - image.width / 2
    var offset = ySize
    if (chromaRowPadding == 0) {
        vBuffer.get(nv21, offset, vSize)
        offset += vSize
        uBuffer.get(nv21, offset, uSize)
    } else {
        // Byte by byte copy with padding skip
        for (row in 0 until image.height / 2) {
            vBuffer.get(nv21, offset, image.width / 2); offset += image.width / 2
            vBuffer.position(vBuffer.position() + chromaRowPadding)
        }
        for (row in 0 until image.height / 2) {
            uBuffer.get(nv21, offset, image.width / 2); offset += image.width / 2
            uBuffer.position(uBuffer.position() + chromaRowPadding)
        }
    }
    return nv21
}

/** 旋轉（依 rotationDegrees），可選水平鏡像（前鏡頭統一方向時用） */
fun Bitmap.rotateAndMirror(rotationDegrees: Int, mirror: Boolean): Bitmap {
    val m = Matrix()
    m.postRotate(rotationDegrees.toFloat())
    if (mirror) m.postScale(-1f, 1f, width / 2f, height / 2f)
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

/** 以人臉框裁切，加入 margin，並截進邊界 */
fun Bitmap.cropWithMargin(box: Rect, margin: Float): Bitmap {
    val centerX = box.exactCenterX()
    val centerY = box.exactCenterY()
    val halfWidth = box.width() / 2f * (1 + margin)
    val halfHeight = box.height() / 2f * (1 + margin)

    val left = (centerX - halfWidth).toInt().coerceAtLeast(0)
    val top = (centerY - halfHeight).toInt().coerceAtLeast(0)
    val right = (centerX + halfWidth).toInt().coerceAtMost(this.width)
    val bottom = (centerY + halfHeight).toInt().coerceAtMost(this.height)

    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
//    return Bitmap.createBitmap(this, box.left, box.top, box.width(), box.height())
}

fun Bitmap.toFace112(): Bitmap {
    val targetSize = 112

    val scale = min(
        targetSize.toFloat() / width,
        targetSize.toFloat() / height
    )

    val scaledWidth = (width * scale).toInt()
    val scaledHeight = (height * scale).toInt()

    val dx = (targetSize - scaledWidth) / 2f
    val dy = (targetSize - scaledHeight) / 2f

    val matrix = Matrix().apply {
        postScale(scale, scale)
        postTranslate(dx, dy)
    }

    val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.BLACK) // 補黑邊
    canvas.drawBitmap(this, matrix, null)

    return output
}