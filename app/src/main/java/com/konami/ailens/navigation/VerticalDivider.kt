package com.konami.ailens.navigation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView

class VerticalDivider(
    private val height: Int,      // divider 的高度（線條厚度）
    private val lineColor: Int,       // divider 的顏色
    private val paddingStart: Int = 0,  // 左邊距
    private val paddingEnd: Int = 0     // 右邊距
) : RecyclerView.ItemDecoration() {

    private val paint = Paint().apply {
        this.color = lineColor
        style = Paint.Style.FILL
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: android.view.View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        // 在每個 item 下方添加間距（除了最後一個）
        val position = parent.getChildAdapterPosition(view)
        if (position < (parent.adapter?.itemCount ?: 0) - 1) {
            outRect.bottom = height
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = parent.paddingLeft + paddingStart
        val right = parent.width - parent.paddingRight - paddingEnd

        // 在每個 item 下方畫線（除了最後一個）
        for (i in 0 until parent.childCount - 1) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams

            val top = child.bottom + params.bottomMargin
            val bottom = top + height

            c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        }
    }
}
