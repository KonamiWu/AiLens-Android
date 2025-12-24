package com.konami.ailens.view

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
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
    var isCacheFrozen: Boolean = false

    var enableDynamicBlurUpdate: Boolean = false

    var cornerRadiusPx: Float = 0f
        set(value) {
            field = max(0f, value)
            isCacheDirty = true
            invalidate()
        }

    @ColorInt
    var fillColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            isCacheDirty = true
            invalidate()
        }

    var fillAlpha: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            isCacheDirty = true
            invalidate()
        }

    @ColorInt
    var innerShadowColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            isCacheDirty = true
            invalidate()
        }

    var innerShadowAlpha: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            isCacheDirty = true
            invalidate()
        }

    var innerShadowOffsetXPx: Float = 0f
        set(value) {
            field = value
            isCacheDirty = true
            invalidate()
        }

    var innerShadowOffsetYPx: Float = 0f
        set(value) {
            field = value
            isCacheDirty = true
            invalidate()
        }

    var innerShadowBlurPx: Float = 0f
        set(value) {
            field = value
            isCacheDirty = true
            invalidate()
        }

    var strokeWidthPx: Float = 0f
        set(value) {
            field = max(0f, value)
            isCacheDirty = true
            invalidate()
        }

    @ColorInt
    var strokeColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            isCacheDirty = true
            invalidate()
        }

    var strokeStartAlpha: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            isCacheDirty = true
            invalidate()
        }

    var strokeEndAlpha: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            isCacheDirty = true
            invalidate()
        }

    /**
     * Blur radius in px (same concept as iOS UIVisualEffectView blur strength).
     */
    var backgroundBlurRadius: Float = 0f
        set(value) {
            field = max(0f, value)
            needsSnapshot = field > 0f
            isCacheDirty = true
            invalidate()
        }

    /**
     * Performance knob.
     * 0.25f means capture at 1/4 size then scale up.
     * Increase (e.g. 0.35f) for sharper details; decrease for better perf.
     */
    var blurDownsample: Float = 1f
        set(value) {
            field = value.coerceIn(0.1f, 1f)
            recycleSnapshot()
            needsSnapshot = backgroundBlurRadius > 0f
            isCacheDirty = true
            invalidate()
        }

    /**
     * Mimic iOS dynamic blur update: this is how often we refresh the captured background.
     * 16ms ~ 60fps, 33ms ~ 30fps, 50-80ms if you want lighter load.
     */
    var blurUpdateIntervalMs: Long = 0L

    // ---------- Internal drawing ----------
    private val rect = RectF()
    private val insideRect = RectF()
    private val clipPath = Path()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private var rootForCapture: View? = null

    private var snapshotBitmap: Bitmap? = null
    private var needsSnapshot: Boolean = false
    private var lastSnapshotUptimeMs: Long = 0L

    private var renderingCache: Bitmap? = null
    private var isCacheDirty: Boolean = true
    private val cachePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Prevent feedback loop: when capturing root, skip drawing this view and parent.
    private var isCapturing: Boolean = false
    override fun draw(canvas: Canvas) {
        if (isCapturing) return
        super.draw(canvas)
    }

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (enableDynamicBlurUpdate && backgroundBlurRadius > 0f && !isCacheFrozen) {
            needsSnapshot = true
            isCacheDirty = true
        }
        true
    }


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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        rootForCapture = findCaptureRoot()
        rootForCapture?.viewTreeObserver?.addOnPreDrawListener(preDrawListener)

        needsSnapshot = backgroundBlurRadius > 0f
    }

    override fun onDetachedFromWindow() {
        rootForCapture?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
        rootForCapture = null
        recycleSnapshot()
        renderingCache?.recycle()
        renderingCache = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recycleSnapshot()
        needsSnapshot = backgroundBlurRadius > 0f
        isCacheDirty = true
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isCapturing) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            super.dispatchDraw(canvas)
            return
        }

        val needsRebuild = (isCacheDirty || renderingCache == null ||
            renderingCache?.width != w.toInt() || renderingCache?.height != h.toInt())

        if (needsRebuild && !isCacheFrozen) {
            rebuildRenderingCache()
        }

        renderingCache?.let {
            canvas.drawBitmap(it, 0f, 0f, cachePaint)
        } ?: run {
            super.dispatchDraw(canvas)
        }
    }

    private fun rebuildRenderingCache() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Recycle old cache
        renderingCache?.recycle()

        // Create new bitmap
        renderingCache = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val cacheCanvas = Canvas(renderingCache!!)

        rect.set(0f, 0f, w, h)
        clipPath.reset()
        clipPath.addRoundRect(rect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)

        maybeUpdateSnapshot()

        val save = cacheCanvas.save()
        cacheCanvas.clipPath(clipPath)

        drawBlurredBackground(cacheCanvas)
        drawFill(cacheCanvas, w, h)
        drawInnerShadow(cacheCanvas, w, h)

        super.dispatchDraw(cacheCanvas)

        drawStroke(cacheCanvas, w, h)

        cacheCanvas.restoreToCount(save)

        isCacheDirty = false
    }

    private fun drawBlurredBackground(canvas: Canvas) {
        val bmp = snapshotBitmap ?: return
        if (backgroundBlurRadius <= 0f) return

        canvas.drawBitmap(bmp, null, rect, blurPaint)
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
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)
        fillPaint.shader = null
    }

    private fun drawInnerShadow(canvas: Canvas, w: Float, h: Float) {
        if (innerShadowColor == Color.TRANSPARENT || innerShadowAlpha <= 0f) return
        if (innerShadowBlurPx <= 0f) return

        val innerSave = canvas.save()
        val shadowSize = innerShadowBlurPx
        val oy = innerShadowOffsetYPx
        val ox = innerShadowOffsetXPx

        // Vertical shadows (top/bottom)
        if (oy != 0f) {
            val strength = kotlin.math.min(1f, kotlin.math.abs(oy) / shadowSize)
            val a = (innerShadowAlpha * strength * 255f).toInt().coerceIn(0, 255)
            val colorStrong = ColorUtils.setAlphaComponent(innerShadowColor, a)
            val colorWeak = ColorUtils.setAlphaComponent(innerShadowColor, 0)

            if (oy < 0f) {
                // Light from bottom, shadow on top
                val shadowBottom = shadowSize
                innerPaint.shader = LinearGradient(
                    0f, 0f, 0f, shadowBottom,
                    intArrayOf(colorStrong, colorWeak),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, shadowBottom, innerPaint)
            } else {
                // Light from top, shadow on bottom
                val shadowTop = h - shadowSize
                innerPaint.shader = LinearGradient(
                    0f, h, 0f, shadowTop,
                    intArrayOf(colorStrong, colorWeak),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, shadowTop, w, h, innerPaint)
            }
        }

        // Horizontal shadows (left/right)
        if (ox != 0f) {
            val strength = kotlin.math.min(1f, kotlin.math.abs(ox) / shadowSize)
            val a = (innerShadowAlpha * strength * 255f).toInt().coerceIn(0, 255)
            val colorStrong = ColorUtils.setAlphaComponent(innerShadowColor, a)
            val colorWeak = ColorUtils.setAlphaComponent(innerShadowColor, 0)

            if (ox < 0f) {
                // Light from right, shadow on left
                val shadowRight = shadowSize
                innerPaint.shader = LinearGradient(
                    0f, 0f, shadowRight, 0f,
                    intArrayOf(colorStrong, colorWeak),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, shadowRight, h, innerPaint)
            } else {
                // Light from left, shadow on right
                val shadowLeft = w - shadowSize
                innerPaint.shader = LinearGradient(
                    w, 0f, shadowLeft, 0f,
                    intArrayOf(colorStrong, colorWeak),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(shadowLeft, 0f, w, h, innerPaint)
            }
        }

        innerPaint.shader = null
        canvas.restoreToCount(innerSave)
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
    }

    // ---------- Snapshot + blur ----------
    private fun maybeUpdateSnapshot() {
        if (!needsSnapshot) return
        if (backgroundBlurRadius <= 0f) return

        val now = SystemClock.uptimeMillis()
        if (now - lastSnapshotUptimeMs < blurUpdateIntervalMs) return
        lastSnapshotUptimeMs = now
        needsSnapshot = false

        val root = rootForCapture ?: return
        if (width <= 0 || height <= 0) return

        val ds = blurDownsample
        val bw = (width * ds).toInt().coerceAtLeast(1)
        val bh = (height * ds).toInt().coerceAtLeast(1)

        val bmp = obtainSnapshotBitmap(bw, bh)
        bmp.eraseColor(Color.TRANSPARENT)

        val c = Canvas(bmp)
        c.save()
        c.scale(ds, ds)

        // Align to root's coordinate (more stable than raw screen-only offsets)
        val rootLoc = IntArray(2)
        val viewLoc = IntArray(2)
        root.getLocationOnScreen(rootLoc)
        getLocationOnScreen(viewLoc)
        val dx = (viewLoc[0] - rootLoc[0]).toFloat()
        val dy = (viewLoc[1] - rootLoc[1]).toFloat()

        c.translate(-dx, -dy)

        isCapturing = true
        val parentView = parent as? View
        val originalParentVisibility = parentView?.visibility
        try {
            parentView?.visibility = View.INVISIBLE
            root.draw(c)
        } finally {
            parentView?.visibility = originalParentVisibility ?: View.VISIBLE
            isCapturing = false
        }
        c.restore()

        val r = (backgroundBlurRadius * ds).toInt().coerceAtLeast(0)
        if (r > 0) {
            stackBlurInPlace(bmp, r)
        }

        snapshotBitmap = bmp
    }

    private fun obtainSnapshotBitmap(w: Int, h: Int): Bitmap {
        val cur = snapshotBitmap
        if (cur != null && !cur.isRecycled && cur.width == w && cur.height == h) {
            return cur
        }
        cur?.recycle()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun recycleSnapshot() {
        snapshotBitmap?.recycle()
        snapshotBitmap = null
    }

    private fun findCaptureRoot(): View? {
        val act = context as? Activity ?: return rootView
        // Prefer content root (avoids some window decorations)
        return act.window?.decorView?.findViewById(android.R.id.content) ?: act.window?.decorView ?: rootView
    }

    // ---------- StackBlur (in-place), used on API < 31 ----------
    // Based on classic StackBlur approach; good quality/perf for UI blur.
    private fun stackBlurInPlace(bitmap: Bitmap, radius: Int) {
        if (radius < 1) return

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        val vmin = IntArray(max(w, h))

        val divsum = ((div + 1) shr 1) * ((div + 1) shr 1)
        val dv = IntArray(256 * divsum)
        for (t in dv.indices) dv[t] = t / divsum

        yi = 0
        var yw = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        // Horizontal pass
        for (y0 in 0 until h) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0

            for (i0 in -radius..radius) {
                p = pix[yi + minOf(wm, maxOf(i0, 0))]
                sir = stack[i0 + radius]
                sir[0] = (p shr 16) and 0xFF
                sir[1] = (p shr 8) and 0xFF
                sir[2] = p and 0xFF
                rbs = r1 - kotlin.math.abs(i0)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i0 > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }

            stackpointer = radius
            for (x0 in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y0 == 0) vmin[x0] = minOf(x0 + radius + 1, wm)
                p = pix[yw + vmin[x0]]

                sir[0] = (p shr 16) and 0xFF
                sir[1] = (p shr 8) and 0xFF
                sir[2] = p and 0xFF

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        // Vertical pass
        for (x0 in 0 until w) {
            rinsum = 0; ginsum = 0; binsum = 0
            routsum = 0; goutsum = 0; boutsum = 0
            rsum = 0; gsum = 0; bsum = 0

            yp = -radius * w
            for (i0 in -radius..radius) {
                yi = maxOf(0, yp) + x0
                sir = stack[i0 + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - kotlin.math.abs(i0)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (i0 > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i0 < hm) yp += w
            }

            yi = x0
            stackpointer = radius
            for (y0 in 0 until h) {
                val a = (pix[yi] ushr 24) and 0xFF
                pix[yi] = (a shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x0 == 0) vmin[y0] = minOf(y0 + r1, hm) * w
                p = x0 + vmin[y0]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    fun refreshBlur() {
        if (backgroundBlurRadius > 0f) {
            recycleSnapshot()
            needsSnapshot = true
            isCacheDirty = true
            invalidate()
        }
    }

    fun clearCache() {
        recycleSnapshot()
        renderingCache?.recycle()
        renderingCache = null
        needsSnapshot = backgroundBlurRadius > 0f
        isCacheDirty = true
    }
}
