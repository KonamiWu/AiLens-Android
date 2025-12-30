package com.konami.ailens.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.konami.ailens.R
import kotlin.math.max

class BlurBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    // ---------- Public knobs ----------
    var cornerRadiusPx: Float = 0f
        set(value) { field = max(0f, value); invalidate() }

    @ColorInt
    var fillColor: Int = Color.TRANSPARENT
        set(value) { field = value; invalidate() }

    var fillAlpha: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    @ColorInt
    var innerShadowColor: Int = Color.TRANSPARENT
        set(value) { field = value; invalidate() }

    var innerShadowAlpha: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    var innerShadowOffsetXPx: Float = 0f
        set(value) { field = value; invalidate() }

    var innerShadowOffsetYPx: Float = 0f
        set(value) { field = value; invalidate() }

    var innerShadowBlurPx: Float = 0f
        set(value) { field = value; invalidate() }

    var strokeWidthPx: Float = 0f
        set(value) { field = max(0f, value); invalidate() }

    @ColorInt
    var strokeColor: Int = Color.TRANSPARENT
        set(value) { field = value; invalidate() }

    var strokeStartAlpha: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    var strokeEndAlpha: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    /** Blur radius in px (view space). */
    var backgroundBlurRadius: Float = 0f
        set(value) { field = max(0f, value); invalidate() }

    /**
     * Capture scale (same semantics as your original code):
     * 0.25f means capture at 1/4 size then scale up.
     */
    var blurDownsample: Float = 1f
        set(value) { field = value.coerceIn(0.1f, 1f); invalidate() }

    /** How often host updates snapshot. If 0, host uses its default. */
    var blurUpdateIntervalMs: Long = 0L
        set(value) { field = max(0L, value); invalidate() }

    // ---------- Internal drawing ----------
    private val rectF = RectF()
    private val insideRect = RectF()
    private val clipPath = Path()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private val srcRect = Rect()
    private val dstRect = Rect()

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false

        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.BlurBorderView)
            try {
                cornerRadiusPx = ta.getDimension(R.styleable.BlurBorderView_blurCornerRadius, cornerRadiusPx)
                fillColor = ta.getColor(R.styleable.BlurBorderView_blurFillColor, fillColor)
                fillAlpha = ta.getFloat(R.styleable.BlurBorderView_blurFillAlpha, fillAlpha)
                innerShadowColor = ta.getColor(R.styleable.BlurBorderView_blurInnerShadowColor, innerShadowColor)
                innerShadowAlpha = ta.getFloat(R.styleable.BlurBorderView_blurInnerShadowAlpha, innerShadowAlpha)
                innerShadowOffsetXPx = ta.getDimension(R.styleable.BlurBorderView_blurInnerShadowOffsetX, innerShadowOffsetXPx)
                innerShadowOffsetYPx = ta.getDimension(R.styleable.BlurBorderView_blurInnerShadowOffsetY, innerShadowOffsetYPx)
                innerShadowBlurPx = ta.getDimension(R.styleable.BlurBorderView_blurInnerShadowBlur, innerShadowBlurPx)
                strokeWidthPx = ta.getDimension(R.styleable.BlurBorderView_blurStrokeWidth, strokeWidthPx)
                strokeColor = ta.getColor(R.styleable.BlurBorderView_blurStrokeColor, strokeColor)
                strokeStartAlpha = ta.getFloat(R.styleable.BlurBorderView_blurStrokeStartAlpha, strokeStartAlpha)
                strokeEndAlpha = ta.getFloat(R.styleable.BlurBorderView_blurStrokeEndAlpha, strokeEndAlpha)
                backgroundBlurRadius = ta.getDimension(R.styleable.BlurBorderView_blurBackgroundRadius, backgroundBlurRadius)
                blurDownsample = ta.getFloat(R.styleable.BlurBorderView_blurDownsample, blurDownsample)
                blurUpdateIntervalMs = ta.getInt(R.styleable.BlurBorderView_blurUpdateInterval, blurUpdateIntervalMs.toInt()).toLong()
            } finally {
                ta.recycle()
            }
        }
    }

    private fun findHost(): BlurHostLayout? {
        var p = parent
        while (p is android.view.View) {
            if (p is BlurHostLayout) return p
            p = p.parent
        }
        return null
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            super.dispatchDraw(canvas)
            return
        }

        rectF.set(0f, 0f, w, h)
        clipPath.reset()
        clipPath.addRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)

        if (backgroundBlurRadius > 0f) {
            findHost()?.drawBlurBehind(
                targetView = this,
                canvas = canvas,
                clipPath = clipPath,
                radiusPx = backgroundBlurRadius,
                downsample = blurDownsample,
                updateIntervalMs = blurUpdateIntervalMs,
                paint = blurPaint,
                srcRectReuse = srcRect,
                dstRectReuse = dstRect
            )
        }

        drawFill(canvas, w, h)

        val save = canvas.save()
        canvas.clipPath(clipPath)
        drawInnerShadow(canvas, w, h)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)

        drawStroke(canvas, w, h)
    }

    private fun drawFill(canvas: Canvas, w: Float, h: Float) {
        if (fillColor == Color.TRANSPARENT || fillAlpha <= 0f) return

        val a = (255f * fillAlpha).toInt().coerceIn(0, 255)
        fillPaint.shader = LinearGradient(
            0f, h, 0f, 0f,
            intArrayOf(
                ColorUtils.setAlphaComponent(fillColor, a),
                ColorUtils.setAlphaComponent(fillColor, 0)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, fillPaint)
        fillPaint.shader = null
    }

    private fun drawInnerShadow(canvas: Canvas, w: Float, h: Float) {
        if (innerShadowColor == Color.TRANSPARENT || innerShadowAlpha <= 0f) return
        if (innerShadowBlurPx <= 0f) return

        val shadowSize = innerShadowBlurPx
        val oy = innerShadowOffsetYPx
        val ox = innerShadowOffsetXPx

        val save = canvas.save()
        // Vertical
        if (oy != 0f) {
            val strength = minOf(1f, kotlin.math.abs(oy) / shadowSize)
            val a = (innerShadowAlpha * strength * 255f).toInt().coerceIn(0, 255)
            val colorStrong = ColorUtils.setAlphaComponent(innerShadowColor, a)
            val colorWeak = ColorUtils.setAlphaComponent(innerShadowColor, 0)

            if (oy < 0f) {
                innerPaint.shader = LinearGradient(
                    0f, 0f, 0f, shadowSize,
                    intArrayOf(colorStrong, colorWeak),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, shadowSize, innerPaint)
            } else {
                val top = h - shadowSize
                innerPaint.shader = LinearGradient(
                    0f, h, 0f, top,
                    intArrayOf(colorStrong, colorWeak),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, top, w, h, innerPaint)
            }
        }

        // Horizontal
        if (ox != 0f) {
            val strength = minOf(1f, kotlin.math.abs(ox) / shadowSize)
            val a = (innerShadowAlpha * strength * 255f).toInt().coerceIn(0, 255)
            val colorStrong = ColorUtils.setAlphaComponent(innerShadowColor, a)
            val colorWeak = ColorUtils.setAlphaComponent(innerShadowColor, 0)

            if (ox < 0f) {
                innerPaint.shader = LinearGradient(
                    0f, 0f, shadowSize, 0f,
                    intArrayOf(colorStrong, colorWeak),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, shadowSize, h, innerPaint)
            } else {
                val left = w - shadowSize
                innerPaint.shader = LinearGradient(
                    w, 0f, left, 0f,
                    intArrayOf(colorStrong, colorWeak),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(left, 0f, w, h, innerPaint)
            }
        }

        innerPaint.shader = null
        canvas.restoreToCount(save)
    }

    private fun drawStroke(canvas: Canvas, w: Float, h: Float) {
        if (strokeWidthPx <= 0f || strokeColor == Color.TRANSPARENT) return

        val inset = strokeWidthPx / 2f
        insideRect.set(inset, inset, w - inset, h - inset)

        val cStart = ColorUtils.setAlphaComponent(strokeColor, (strokeStartAlpha * 255f).toInt())
        val cEnd = ColorUtils.setAlphaComponent(strokeColor, (strokeEndAlpha * 255f).toInt())

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = strokeWidthPx
        strokePaint.isAntiAlias = true
        strokePaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(cStart, cEnd),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            insideRect,
            max(0f, cornerRadiusPx - inset),
            max(0f, cornerRadiusPx - inset),
            strokePaint
        )
        strokePaint.shader = null
    }
}
