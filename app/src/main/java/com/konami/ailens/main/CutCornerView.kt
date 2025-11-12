package com.konami.ailens.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.konami.ailens.R

class CutCornerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var cornerCut = 12f
    var borderColor: Int = Color.WHITE
    var borderWidth: Float = 2f
    var fillColor: Int = Color.TRANSPARENT
    var fillColorAlpha: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var cutCorners: Int =
        CUT_TOP_LEFT or CUT_TOP_RIGHT or CUT_BOTTOM_RIGHT or CUT_BOTTOM_LEFT
    var backgroundImage: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    private var cornerProgress = floatArrayOf(0f, 0f, 0f, 0f)

    private val shapePath = Path()
    private val borderPath = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)

        context.theme.obtainStyledAttributes(attrs, R.styleable.CutCornerView, 0, 0).apply {
            try {
                cornerCut = getDimension(R.styleable.CutCornerView_cornerCut, cornerCut)
                borderWidth = getDimension(R.styleable.CutCornerView_borderWidth, borderWidth)
                borderColor = getColor(R.styleable.CutCornerView_borderColor, borderColor)
                fillColor = getColor(R.styleable.CutCornerView_fillColor, fillColor)
                fillColorAlpha = getFloat(R.styleable.CutCornerView_fillColorAlpha, fillColorAlpha)
                cutCorners = getInt(
                    R.styleable.CutCornerView_cutCorners,
                    CUT_TOP_LEFT or CUT_TOP_RIGHT or CUT_BOTTOM_RIGHT or CUT_BOTTOM_LEFT
                )
                for (i in 0..3) {
                    cornerProgress[i] =
                        if (cutCorners and (1 shl i) != 0) cornerCut else 0f
                }
            } finally {
                recycle()
            }
        }
    }

    private fun updatePaths() {
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = borderWidth / 2f

        val cTL = cornerProgress[0]
        val cTR = cornerProgress[1]
        val cBR = cornerProgress[2]
        val cBL = cornerProgress[3]

        shapePath.reset()
        shapePath.moveTo(if (cTL > 0) cTL else 0f, 0f)
        shapePath.lineTo(if (cTR > 0) w - cTR else w, 0f)
        if (cTR > 0) shapePath.lineTo(w, cTR)
        shapePath.lineTo(w, if (cBR > 0) h - cBR else h)
        if (cBR > 0) shapePath.lineTo(w - cBR, h)
        shapePath.lineTo(if (cBL > 0) cBL else 0f, h)
        if (cBL > 0) shapePath.lineTo(0f, h - cBL)
        shapePath.lineTo(0f, if (cTL > 0) cTL else 0f)
        shapePath.close()

        borderPath.reset()
        borderPath.moveTo(if (cTL > 0) cTL + inset else inset, inset)
        borderPath.lineTo(if (cTR > 0) w - cTR - inset else w - inset, inset)
        if (cTR > 0) borderPath.lineTo(w - inset, cTR + inset)
        borderPath.lineTo(w - inset, if (cBR > 0) h - cBR - inset else h - inset)
        if (cBR > 0) borderPath.lineTo(w - cBR - inset, h - inset)
        borderPath.lineTo(if (cBL > 0) cBL + inset else inset, h - inset)
        if (cBL > 0) borderPath.lineTo(inset, h - cBL - inset)
        borderPath.lineTo(inset, if (cTL > 0) cTL + inset else inset)
        borderPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        updatePaths()

        val saveCount = canvas.save()
        canvas.clipPath(shapePath)

        backgroundImage?.let { bitmap ->
            val srcRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            val dstRect = RectF(0f, 0f, w, h)
            canvas.drawBitmap(bitmap, null, dstRect, bitmapPaint)
        }

        val colorAlpha = Color.alpha(fillColor) / 255f
        val combinedAlpha = colorAlpha * fillColorAlpha
        fillPaint.color = fillColor
        fillPaint.alpha = (combinedAlpha * 255).toInt()
        canvas.drawPath(shapePath, fillPaint)

        canvas.restoreToCount(saveCount)

        borderPaint.color = borderColor
        borderPaint.strokeWidth = borderWidth
        canvas.drawPath(borderPath, borderPaint)
    }

    override fun dispatchDraw(canvas: Canvas) {
        updatePaths()

        val saveCount = canvas.save()
        canvas.clipPath(shapePath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    fun animateAddCorners(targetFlags: Int, duration: Long = 800L) {
        animateCornerChange(targetFlags, duration)
    }

    fun animateRemoveCorners(targetFlags: Int, duration: Long = 800L) {
        animateCornerChange(targetFlags, duration)
    }

    fun animateToCorners(targetFlags: Int, duration: Long = 800L) {
        animateCornerChange(targetFlags, duration)
    }

    private fun animateCornerChange(targetFlags: Int, duration: Long) {
        val startCuts = cornerProgress.copyOf()
        val endCuts = FloatArray(4) { i ->
            if (targetFlags and (1 shl i) != 0) cornerCut else 0f
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedFraction
                for (i in 0..3) {
                    val start = startCuts[i]
                    val end = endCuts[i]
                    cornerProgress[i] = start + (end - start) * f
                }
                invalidate()
            }
            start()
        }

        cutCorners = targetFlags
    }

    companion object {
        const val CUT_TOP_LEFT = 1 shl 0
        const val CUT_TOP_RIGHT = 1 shl 1
        const val CUT_BOTTOM_RIGHT = 1 shl 2
        const val CUT_BOTTOM_LEFT = 1 shl 3
    }
}
