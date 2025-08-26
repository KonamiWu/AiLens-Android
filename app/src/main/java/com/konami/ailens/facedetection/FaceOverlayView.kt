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
    val transformMatrix = Matrix()
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

    private var testWidth = 0f
    private var testHeight = 0f

    private var faceResult = listOf<FaceAnalyzer.RecognizedFace>()

    fun updateResult(result: List<FaceAnalyzer.RecognizedFace>) {
        faceResult = result
        invalidate()
    }

    fun setTransformInfo(
        imageWidth: Int, imageHeight: Int,
        viewWidth: Int, viewHeight: Int,
        rotationDegrees: Int,
        isFrontCamera: Boolean
    ) {
        var calibratedWidth = 0f
        var calibratedHeight = 0f
        if (rotationDegrees == 0 || rotationDegrees == 180) {
            calibratedWidth = imageWidth.toFloat()
            calibratedHeight = imageHeight.toFloat()
        } else {
            calibratedWidth = imageHeight.toFloat()
            calibratedHeight = imageWidth.toFloat()
        }
        testWidth = calibratedWidth
        testHeight = calibratedHeight

        transformMatrix.reset()
        var x = 0f
        var y = 0f
        var ratio = 1f
        val imageHeightOverWidth = calibratedHeight.toFloat() / calibratedWidth
        val viewHeightOverWidth = viewHeight.toFloat() / viewWidth
        if (imageHeightOverWidth > viewHeightOverWidth) {
            ratio = viewHeight.toFloat() / calibratedHeight
            val horizontalRemaining = viewWidth - calibratedWidth * ratio
            val horizontalOffset = horizontalRemaining / 2
            x = horizontalOffset.toFloat()
        } else {
            ratio = viewWidth.toFloat() / calibratedWidth
            val verticalRemaining = viewHeight - calibratedHeight * ratio
            val verticalOffset = verticalRemaining / 2
            y = verticalOffset.toFloat()
        }

        transformMatrix.postScale(ratio, ratio)
        transformMatrix.postTranslate(x, y)
        if (isFrontCamera) {
            transformMatrix.postScale(-1f, 1f, viewWidth.toFloat() / 2, viewHeight.toFloat() / 2)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for(result in faceResult) {
            val rect = RectF(result.box)
            transformMatrix.mapRect(rect)
            canvas.drawRect(rect, paint)
            val name = result.name
            var textRect = Rect()
            textPaint.getTextBounds(name, 0, name.length, textRect)
            canvas.drawText(result.name, rect.centerX() - textRect.width() / 2, rect.centerY(), textPaint)
        }
    }
}