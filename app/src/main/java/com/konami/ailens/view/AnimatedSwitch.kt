package com.konami.ailens.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.widget.SwitchCompat
import com.konami.ailens.R
import com.konami.ailens.resolveAttrColor

class AnimatedSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.switchStyle
) : SwitchCompat(context, attrs, defStyleAttr) {

    private var animationDuration = 200L
    private var currentAnimator: ValueAnimator? = null

    init {
        setThumbResource(R.drawable.switch_thumb)
        setTrackResource(R.drawable.switch_track)

        val initialThumbColor = context.resolveAttrColor(R.attr.appSurface1)
        val initialTrackColor = if (isChecked) {
            context.resolveAttrColor(R.attr.appPrimary)
        } else {
            context.resolveAttrColor(R.attr.appButtonDisable)
        }

        thumbDrawable?.mutate()?.setTint(initialThumbColor)
        trackDrawable?.mutate()?.setTint(initialTrackColor)

        setOnCheckedChangeListener { _, isChecked ->
            animateToState(isChecked)
        }
    }

    override fun setChecked(checked: Boolean) {
        val wasChecked = isChecked
        super.setChecked(checked)

        if (wasChecked != checked && isAttachedToWindow) {
            animateToState(checked)
        }
    }

    private fun animateToState(isChecked: Boolean) {
        val thumb = thumbDrawable?.mutate() ?: return
        val track = trackDrawable?.mutate() ?: return

        val thumbColor = context.resolveAttrColor(R.attr.appSurface1)
        val fromTrackColor = if (isChecked) {
            context.resolveAttrColor(R.attr.appButtonDisable)
        } else {
            context.resolveAttrColor(R.attr.appPrimary)
        }
        val toTrackColor = if (isChecked) {
            context.resolveAttrColor(R.attr.appPrimary)
        } else {
            context.resolveAttrColor(R.attr.appButtonDisable)
        }

        currentAnimator?.cancel()

        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val trackColor = lerpColor(fromTrackColor, toTrackColor, fraction)

                thumb.setTint(thumbColor)
                track.setTint(trackColor)
                invalidate()
            }
        }
        currentAnimator?.start()
    }

    private fun lerpColor(@ColorInt from: Int, @ColorInt to: Int, fraction: Float): Int {
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * fraction).toInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction).toInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction).toInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction).toInt()
        return Color.argb(a, r, g, b)
    }

    fun setAnimationDuration(duration: Long) {
        this.animationDuration = duration
    }
}
