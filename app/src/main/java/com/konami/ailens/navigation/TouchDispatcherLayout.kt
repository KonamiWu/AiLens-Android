package com.konami.ailens.navigation

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Observes all touch events passing through this container without intercepting or consuming.
 * Useful for computing velocity while letting children handle touches normally.
 */
class TouchDispatcherLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var velocityTracker: VelocityTracker? = null
    var velocity: Float = 0f
        private set

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                velocity = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.clear()
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        // Do not interfere with normal dispatch
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // Never intercept; children should receive their touches
        return false
    }
}

