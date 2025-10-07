package com.konami.ailens.main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PathEffect
import android.graphics.DashPathEffect
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.konami.ailens.R

class DashedLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Orientation(val value: Int) {
        HORIZONTAL(0),
        VERTICAL(1);

        companion object {
            fun from(value: Int) = values().firstOrNull { it.value == value } ?: HORIZONTAL
        }
    }

    @ColorInt
    var lineColor: Int = ContextCompat.getColor(context, android.R.color.darker_gray)
    var lineWidth: Float = 1f
    var dashLength: Float = 6f
    var dashSpacing: Float = 4f
    var orientation: Orientation = Orientation.HORIZONTAL

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.DashedLineView, 0, 0).apply {
            try {
                lineColor = getColor(R.styleable.DashedLineView_lineColor, lineColor)
                lineWidth = getDimension(R.styleable.DashedLineView_lineWidth, lineWidth)
                dashLength = getDimension(R.styleable.DashedLineView_dashLength, dashLength)
                dashSpacing = getDimension(R.styleable.DashedLineView_dashSpacing, dashSpacing)
                orientation = Orientation.from(getInt(R.styleable.DashedLineView_orientation, 0))
            } finally {
                recycle()
            }
        }

        paint.color = lineColor
        paint.strokeWidth = lineWidth
        paint.pathEffect = DashPathEffect(floatArrayOf(dashLength, dashSpacing), 0f)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.color = lineColor
        paint.strokeWidth = lineWidth
        paint.pathEffect = DashPathEffect(floatArrayOf(dashLength, dashSpacing), 0f)

        when (orientation) {
            Orientation.HORIZONTAL -> {
                val midY = height / 2f
                canvas.drawLine(0f, midY, width.toFloat(), midY, paint)
            }

            Orientation.VERTICAL -> {
                val midX = width / 2f
                canvas.drawLine(midX, 0f, midX, height.toFloat(), paint)
            }
        }
    }
}
