package com.konami.ailens.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.konami.ailens.R
import kotlin.math.max

/**
 * Figma-like edge glow:
 * - RadialGradient with center on left edge middle (or slightly outside).
 * - Large radius so the energy decays vertically (top/bottom weaker).
 * - Optional Layer Blur via RenderEffect (API 31+).
 */
class GlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---- Color / alpha ----
    @ColorInt
    var baseColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }

    /** Center alpha (0..1). */
    var centerAlpha: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** Edge alpha (0..1). Usually 0.0. */
    var edgeAlpha: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // ---- Position / shape ----
    /**
     * Center position normalized:
     * - x = 0 means left edge
     * - x < 0 means outside (recommended for more natural glow)
     */
    var centerXNorm: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /** y = 0.5 means vertical middle. */
    var centerYNorm: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Radius factor relative to max(width, height).
     * Bigger = softer and more spread out.
     */
    var radiusFactor: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
            invalidate()
        }

    // ---- Figma blur mapping (optional) ----
    /** Use ratio mapping like: blurPx = viewWidth * (figmaBlurPx / figmaLayerWidthPx) */
    var figmaLayerWidthPx: Float = 0f
        set(value) {
            field = value
            applyBlurIfPossible()
        }

    var figmaBlurPx: Float = 0f
        set(value) {
            field = value
            applyBlurIfPossible()
        }

    /** Enable/disable layer blur */
    var blurEnabled: Boolean = false
        set(value) {
            field = value
            applyBlurIfPossible()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)

        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.GlowView)
            try {
                baseColor = ta.getColor(R.styleable.GlowView_glowBaseColor, baseColor)
                centerAlpha = ta.getFloat(R.styleable.GlowView_glowCenterAlpha, centerAlpha)
                edgeAlpha = ta.getFloat(R.styleable.GlowView_glowEdgeAlpha, edgeAlpha)
                centerXNorm = ta.getFloat(R.styleable.GlowView_glowCenterXNorm, centerXNorm)
                centerYNorm = ta.getFloat(R.styleable.GlowView_glowCenterYNorm, centerYNorm)
                radiusFactor = ta.getFloat(R.styleable.GlowView_glowRadiusFactor, radiusFactor)
                figmaLayerWidthPx = ta.getDimension(R.styleable.GlowView_glowFigmaLayerWidthPx, figmaLayerWidthPx)
                figmaBlurPx = ta.getDimension(R.styleable.GlowView_glowFigmaBlurPx, figmaBlurPx)
                blurEnabled = ta.getBoolean(R.styleable.GlowView_glowBlurEnabled, blurEnabled)
            } finally {
                ta.recycle()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyBlurIfPossible()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyBlurIfPossible()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        val cx = w * centerXNorm
        val cy = h * centerYNorm
        val r = max(w, h) * radiusFactor

        val c0 = ColorUtils.setAlphaComponent(baseColor, (centerAlpha * 255f).toInt())
        val c1 = ColorUtils.setAlphaComponent(baseColor, (edgeAlpha * 255f).toInt())

        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(c0, c1),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun applyBlurIfPossible() {
        if (!isAttachedToWindow) return
        if (width <= 0 || height <= 0) return

        if (!blurEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRenderEffect(null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ratio = if (figmaLayerWidthPx > 0f) figmaBlurPx / figmaLayerWidthPx else 0f
            val blurPx = (width.toFloat() * ratio).coerceAtLeast(0f)

            val effect = if (blurPx <= 0.5f) null else {
                RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            }
            setRenderEffect(effect)
        }
    }
}
