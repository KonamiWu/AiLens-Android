package com.konami.ailens.facedetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var faces: List<Rect> = emptyList()
    private var textRect = Rect()
    private val transformMatrix = Matrix()
    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 24 * context.resources.displayMetrics.density
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    private var faceResult = listOf<RecognizedFace>() // ➡️ 這裡的型別應為 RecognizedFace

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for(result in faceResult) {
            val rect = RectF(result.box) // ➡️ 這裡使用 RectF
            setTransformInfo(result.imageWidth, result.imageHeight)
            transformMatrix.mapRect(rect)
            canvas.drawRect(rect, paint)
            val name = result.name
            textPaint.getTextBounds(name, 0, name.length, textRect)
            canvas.drawText(result.name, rect.centerX() - textRect.width() / 2, rect.centerY(), textPaint)
        }
    }

    // ➡️ 修改 updateResult 接收新的資料類別
    fun updateResult(result: List<RecognizedFace>) {
        faceResult = result
        invalidate()
    }

    private fun setTransformInfo(
        imageWidth: Int,
        imageHeight: Int
    ) {
        val iw = imageWidth.toFloat()
        val ih = imageHeight.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()

        transformMatrix.reset()

        val imageAspect = ih / iw
        val viewAspect  = vh / vw

        val scale: Float
        val tx: Float
        val ty: Float
        if (imageAspect > viewAspect) {
            scale = vh / ih
            tx = (vw - iw * scale) / 2f
            ty = 0f
        } else {
            scale = vw / iw
            tx = 0f
            ty = (vh - ih * scale) / 2f
        }

        transformMatrix.postScale(scale, scale)
        transformMatrix.postTranslate(tx, ty)

        // ⚠️ 不要再做前鏡頭水平翻轉；影像已在 ViewModel 鏡像過了
    }


    private fun setTransformInfo1(
        imageWidth: Int,
        imageHeight: Int
    ) {
        val iw = imageWidth.toFloat()
        val ih = imageHeight.toFloat()

        transformMatrix.reset()

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val imageAspect = ih / iw
        val viewAspect  = viewH / viewW

        var tx = 0f
        var ty = 0f
        val scale = if (imageAspect > viewAspect) {
            // 以高對齊（上下貼齊，左右留黑）
            val s = viewH / ih
            tx = (viewW - iw * s) / 2f
            s
        } else {
            // 以寬對齊（左右貼齊，上下留黑）
            val s = viewW / iw
            ty = (viewH - ih * s) / 2f
            s
        }

        transformMatrix.postScale(scale, scale)
        transformMatrix.postTranslate(tx, ty)
    }
}