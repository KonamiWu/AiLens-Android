package com.konami.ailens.main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.konami.ailens.R

class CutCornerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var cornerCut = 12f
    var borderColor: Int = Color.WHITE
    var borderWidth: Float = 1f
    var fillColor: Int = Color.TRANSPARENT

    private val path = Path()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.CutCornerView, 0, 0).apply {
            try {
                cornerCut = getDimension(R.styleable.CutCornerView_cornerCut, cornerCut)
                borderWidth = getDimension(R.styleable.CutCornerView_borderWidth, borderWidth)
                borderColor = getColor(R.styleable.CutCornerView_borderColor, borderColor)
                fillColor = getColor(R.styleable.CutCornerView_fillColor, fillColor)
            } finally {
                recycle()
            }
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val c = cornerCut

        path.reset()
        path.moveTo(c, 0f)
        path.lineTo(w - c, 0f)
        path.lineTo(w, c)
        path.lineTo(w, h - c)
        path.lineTo(w - c, h)
        path.lineTo(c, h)
        path.lineTo(0f, h - c)
        path.lineTo(0f, c)
        path.close()

        // 填充背景
        fillPaint.color = fillColor
        canvas.drawPath(path, fillPaint)

        // 畫邊框（向內縮 1/2 border）
        val borderInset = borderWidth / 2f
        val borderRect = RectF(
            borderInset,
            borderInset,
            w - borderInset,
            h - borderInset
        )

        val borderPath = Path()
        borderPath.moveTo(c + borderInset, borderInset)
        borderPath.lineTo(w - c - borderInset, borderInset)
        borderPath.lineTo(w - borderInset, c + borderInset)
        borderPath.lineTo(w - borderInset, h - c - borderInset)
        borderPath.lineTo(w - c - borderInset, h - borderInset)
        borderPath.lineTo(c + borderInset, h - borderInset)
        borderPath.lineTo(borderInset, h - c - borderInset)
        borderPath.lineTo(borderInset, c + borderInset)
        borderPath.close()

        borderPaint.color = borderColor
        borderPaint.strokeWidth = borderWidth
        canvas.drawPath(borderPath, borderPaint)
    }
}


/*
init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.CutCornerView, 0, 0).apply {
            try {
                cornerCut = getDimension(R.styleable.CutCornerView_cornerCut, cornerCut)
                borderWidth = getDimension(R.styleable.CutCornerView_borderWidth, borderWidth)
                borderColor = getColor(R.styleable.CutCornerView_borderColor, borderColor)
                fillColor = getColor(R.styleable.CutCornerView_fillColor, fillColor)
            } finally {
                recycle()
            }
        }
    }
 */