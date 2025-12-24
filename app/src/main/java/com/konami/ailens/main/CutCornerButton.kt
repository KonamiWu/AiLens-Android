package com.konami.ailens.main

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatButton
import com.konami.ailens.R

class CutCornerButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatButton(context, attrs, defStyleAttr) {

    enum class CornerStyle {
        BEVEL,
        ROUND
    }

    var cornerCut = 12f
    var borderColor: Int = Color.TRANSPARENT
    var borderWidth: Float = 0f
    var fillColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }
    var fillColorAlpha: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var pressColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }
    var disableFillColor: Int = Color.TRANSPARENT
    var disableTextColor: Int = Color.TRANSPARENT

    private var enabledFillColor: Int = Color.TRANSPARENT
    private var enabledTextColor: Int = Color.TRANSPARENT
    private var colorAnimator: ValueAnimator? = null
    var cutCorners: Int =
        CUT_TOP_LEFT or CUT_TOP_RIGHT or CUT_BOTTOM_RIGHT or CUT_BOTTOM_LEFT
    var cornerStyle: CornerStyle = CornerStyle.BEVEL
        set(value) {
            field = value
            invalidate()
        }
    var backgroundImage: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var progressColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private var isPressedState = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var cornerProgress = floatArrayOf(0f, 0f, 0f, 0f)

    private val shapePath = Path()
    private val borderPath = Path()
    private val progressPath = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        background = null
        gravity = android.view.Gravity.CENTER

        context.theme.obtainStyledAttributes(attrs, R.styleable.CutCornerButton, 0, 0).apply {
            try {
                cornerCut = getDimension(R.styleable.CutCornerButton_cornerCut, cornerCut)
                borderWidth = getDimension(R.styleable.CutCornerButton_borderWidth, borderWidth)
                borderColor = getColor(R.styleable.CutCornerButton_borderColor, borderColor)
                fillColor = getColor(R.styleable.CutCornerButton_fillColor, fillColor)
                fillColorAlpha = getFloat(R.styleable.CutCornerButton_fillColorAlpha, fillColorAlpha)
                pressColor = getColor(R.styleable.CutCornerButton_pressColor, pressColor)
                disableFillColor = getColor(R.styleable.CutCornerButton_disableFillColor, disableFillColor)
                disableTextColor = getColor(R.styleable.CutCornerButton_disableTextColor, disableTextColor)
                cutCorners = getInt(
                    R.styleable.CutCornerButton_cutCorners,
                    CUT_TOP_LEFT or CUT_TOP_RIGHT or CUT_BOTTOM_RIGHT or CUT_BOTTOM_LEFT
                )
                cornerStyle = when (getInt(R.styleable.CutCornerButton_cornerStyle, 0)) {
                    1 -> CornerStyle.ROUND
                    else -> CornerStyle.BEVEL
                }

                enabledFillColor = fillColor
                enabledTextColor = currentTextColor

                for (i in 0..3) {
                    cornerProgress[i] =
                        if (cutCorners and (1 shl i) != 0) cornerCut else 0f
                }
            } finally {
                recycle()
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) {
            super.setEnabled(enabled)
            return
        }

        super.setEnabled(enabled)

        if (disableFillColor == Color.TRANSPARENT && disableTextColor == Color.TRANSPARENT) {
            return
        }

        colorAnimator?.cancel()

        val startFillColor = fillColor
        val endFillColor = if (enabled) enabledFillColor else disableFillColor
        val startTextColor = currentTextColor
        val endTextColor = if (enabled) enabledTextColor else disableTextColor

        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            val colorEvaluator = ArgbEvaluator()

            addUpdateListener { animator ->
                val fraction = animator.animatedFraction

                if (disableFillColor != Color.TRANSPARENT) {
                    fillColor = colorEvaluator.evaluate(fraction, startFillColor, endFillColor) as Int
                }

                if (disableTextColor != Color.TRANSPARENT) {
                    setTextColor(colorEvaluator.evaluate(fraction, startTextColor, endTextColor) as Int)
                }
            }

            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressedState = true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isPressedState = false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updatePaths() {
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = borderWidth / 2f

        val cTL = cornerProgress[0]
        val cTR = cornerProgress[1]
        val cBR = cornerProgress[2]
        val cBL = cornerProgress[3]

        val isRound = cornerStyle == CornerStyle.ROUND

        shapePath.reset()
        shapePath.moveTo(if (cTL > 0) cTL else 0f, 0f)

        if (cTR > 0) {
            shapePath.lineTo(w - cTR, 0f)
            if (isRound) {
                val rect = RectF(w - cTR * 2, 0f, w, cTR * 2)
                shapePath.arcTo(rect, 270f, 90f)
            } else {
                shapePath.lineTo(w, cTR)
            }
        } else {
            shapePath.lineTo(w, 0f)
        }

        if (cBR > 0) {
            shapePath.lineTo(w, h - cBR)
            if (isRound) {
                val rect = RectF(w - cBR * 2, h - cBR * 2, w, h)
                shapePath.arcTo(rect, 0f, 90f)
            } else {
                shapePath.lineTo(w - cBR, h)
            }
        } else {
            shapePath.lineTo(w, h)
        }

        if (cBL > 0) {
            shapePath.lineTo(cBL, h)
            if (isRound) {
                val rect = RectF(0f, h - cBL * 2, cBL * 2, h)
                shapePath.arcTo(rect, 90f, 90f)
            } else {
                shapePath.lineTo(0f, h - cBL)
            }
        } else {
            shapePath.lineTo(0f, h)
        }

        if (cTL > 0) {
            shapePath.lineTo(0f, cTL)
            if (isRound) {
                val rect = RectF(0f, 0f, cTL * 2, cTL * 2)
                shapePath.arcTo(rect, 180f, 90f)
            } else {
                shapePath.lineTo(cTL, 0f)
            }
        } else {
            shapePath.lineTo(0f, 0f)
        }

        shapePath.close()

        borderPath.reset()
        borderPath.moveTo(if (cTL > 0) cTL + inset else inset, inset)

        if (cTR > 0) {
            borderPath.lineTo(w - cTR - inset, inset)
            if (isRound) {
                val rect = RectF(w - cTR * 2 + inset, inset, w - inset, cTR * 2 - inset)
                borderPath.arcTo(rect, 270f, 90f)
            } else {
                borderPath.lineTo(w - inset, cTR + inset)
            }
        } else {
            borderPath.lineTo(w - inset, inset)
        }

        if (cBR > 0) {
            borderPath.lineTo(w - inset, h - cBR - inset)
            if (isRound) {
                val rect = RectF(w - cBR * 2 + inset, h - cBR * 2 + inset, w - inset, h - inset)
                borderPath.arcTo(rect, 0f, 90f)
            } else {
                borderPath.lineTo(w - cBR - inset, h - inset)
            }
        } else {
            borderPath.lineTo(w - inset, h - inset)
        }

        if (cBL > 0) {
            borderPath.lineTo(cBL + inset, h - inset)
            if (isRound) {
                val rect = RectF(inset, h - cBL * 2 + inset, cBL * 2 - inset, h - inset)
                borderPath.arcTo(rect, 90f, 90f)
            } else {
                borderPath.lineTo(inset, h - cBL - inset)
            }
        } else {
            borderPath.lineTo(inset, h - inset)
        }

        if (cTL > 0) {
            borderPath.lineTo(inset, cTL + inset)
            if (isRound) {
                val rect = RectF(inset, inset, cTL * 2 - inset, cTL * 2 - inset)
                borderPath.arcTo(rect, 180f, 90f)
            } else {
                borderPath.lineTo(cTL + inset, inset)
            }
        } else {
            borderPath.lineTo(inset, inset)
        }

        borderPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        updatePaths()

        backgroundImage?.let { bitmap ->
            val saveCount = canvas.save()
            canvas.clipPath(shapePath)
            val srcRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            val dstRect = RectF(0f, 0f, w, h)
            canvas.drawBitmap(bitmap, null, dstRect, bitmapPaint)
            canvas.restoreToCount(saveCount)
        }

        val currentColor = if (isPressedState && pressColor != Color.TRANSPARENT) pressColor else fillColor
        val colorAlpha = Color.alpha(currentColor) / 255f
        val combinedAlpha = colorAlpha * fillColorAlpha
        fillPaint.color = currentColor
        fillPaint.alpha = (combinedAlpha * 255).toInt()
        canvas.drawPath(shapePath, fillPaint)

        if (progress > 0f) {
            val progressWidth = w * progress
            progressPath.reset()
            progressPath.addRect(0f, 0f, progressWidth, h, Path.Direction.CW)
            progressPath.op(shapePath, Path.Op.INTERSECT)

            progressPaint.color = progressColor
            canvas.drawPath(progressPath, progressPaint)
        }

        borderPaint.color = borderColor
        borderPaint.strokeWidth = borderWidth
        canvas.drawPath(borderPath, borderPaint)

        val saveCount = canvas.save()
        canvas.clipPath(shapePath)
        super.onDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    fun animateProgress(targetProgress: Float, duration: Long = 300L) {
        val startProgress = progress
        val endProgress = targetProgress.coerceIn(0f, 1f)

        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedFraction
                progress = startProgress + (endProgress - startProgress) * f
            }
            start()
        }
    }

    companion object {
        const val CUT_TOP_LEFT = 1 shl 0
        const val CUT_TOP_RIGHT = 1 shl 1
        const val CUT_BOTTOM_RIGHT = 1 shl 2
        const val CUT_BOTTOM_LEFT = 1 shl 3
    }
}
