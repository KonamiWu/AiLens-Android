package com.konami.ailens.translation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.konami.ailens.R
import kotlin.math.min
import androidx.core.content.withStyledAttributes

class WaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dotSize: Float = 0f
    private var dotSpacing: Float = 0f
    private var fillColor = Color.WHITE
    private var dotColor = Color.WHITE

    private var phase: Float = 0f
    private val barRect = RectF()

    private val maxHeightFactor = arrayOf(0.3f, 0.5f, 0.2f)

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 800L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dotColor
    }

    init {
        context.withStyledAttributes(attrs, R.styleable.WaveView) {
            dotSize = getDimension(R.styleable.WaveView_dotSize, 6f * resources.displayMetrics.density)
            dotSpacing = getDimension(R.styleable.WaveView_dotSpacing, 3f * resources.displayMetrics.density)
            fillColor = getColor(R.styleable.WaveView_waveBackgroundColor, Color.WHITE)
            dotColor = getColor(R.styleable.WaveView_dotColor, Color.WHITE)
        }

        circlePaint.color = fillColor
        barPaint.color = dotColor

        if (!isInEditMode) start()
    }

    fun start() {
        if (!animator.isStarted) animator.start()
    }

    fun stop() {
        animator.cancel()
        phase = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f

        canvas.drawCircle(cx, cy, radius, circlePaint)

        val leftX = cx - dotSize - dotSpacing
        val midX = cx
        val rightX = cx + dotSize + dotSpacing
        val startXs = arrayOf(
            leftX - dotSize / 2f,
            midX - dotSize / 2f,
            rightX - dotSize / 2f
        )

        for (i in 0 until 3) {
            val localPhase = (phase + i * 0.2f) % 1f

            val wave = if (localPhase < 0.5f) {
                localPhase * 2f
            } else {
                (1f - localPhase) * 2f
            }

            val extraHeight = radius * maxHeightFactor[i] * wave
            val height = dotSize + extraHeight

            val left = startXs[i]
            val right = left + dotSize
            val top = cy - height / 2f
            val bottom = cy + height / 2f

            barRect.set(left, top, right, bottom)

            canvas.drawRoundRect(barRect, dotSize / 2f, dotSize / 2f, barPaint)
        }
    }
}
