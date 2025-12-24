package com.konami.ailens.view

import android.graphics.Canvas
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView

class FavoriteDivider(
    private val space: Int,   // divider 與 item 之間的間距
    private val lineWidth: Int,       // divider 的寬度（橫向時是「線條厚度」）
    private val lineColor: Int
) : RecyclerView.ItemDecoration() {

    private val paint = Paint().apply {
        this.color = lineColor
        style = Paint.Style.FILL
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val top = parent.paddingTop
        val bottom = parent.height - parent.paddingBottom

        for (i in 0 until parent.childCount - 1) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams

            val left = child.right + params.rightMargin
            val right = left + lineWidth

            c.drawRect(left.toFloat(), top.toFloat() + space, right.toFloat(), bottom.toFloat() - space, paint)
        }
    }
}
