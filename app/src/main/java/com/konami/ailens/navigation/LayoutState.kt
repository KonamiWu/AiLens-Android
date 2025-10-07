package com.konami.ailens.navigation

import android.view.MotionEvent
import android.view.View

interface LayoutState {
    fun onTouch(view: View, event: MotionEvent): Boolean
}