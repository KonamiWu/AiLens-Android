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
    var backgroundImage: Bitmap? = null
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
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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

        borderPaint.color = borderColor
        borderPaint.strokeWidth = borderWidth
        canvas.drawPath(borderPath, borderPaint)

        val saveCount = canvas.save()
        canvas.clipPath(shapePath)
        super.onDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    companion object {
        const val CUT_TOP_LEFT = 1 shl 0
        const val CUT_TOP_RIGHT = 1 shl 1
        const val CUT_BOTTOM_RIGHT = 1 shl 2
        const val CUT_BOTTOM_LEFT = 1 shl 3
    }
}
