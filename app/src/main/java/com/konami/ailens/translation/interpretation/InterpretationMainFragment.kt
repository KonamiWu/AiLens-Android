package com.konami.ailens.translation.interpretation

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentInterpretationMainBinding
import com.konami.ailens.resolveAttrColor

class InterpretationMainFragment: Fragment() {
    private lateinit var binding: FragmentInterpretationMainBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentInterpretationMainBinding.inflate(inflater, container, false)
        binding.bilingualSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.bilingualSwitch.animateColors(
                    fromThumb = requireContext().resolveAttrColor(R.attr.appSurface1),
                    toThumb   = requireContext().resolveAttrColor(R.attr.appSurface1),         // 如果 thumb 顏色一樣就填一樣的
                    fromTrack = requireContext().resolveAttrColor(R.attr.appBackground),
                    toTrack   = requireContext().resolveAttrColor(R.attr.appPrimary)
                )
            } else {
                binding.bilingualSwitch.animateColors(
                    fromThumb = requireContext().resolveAttrColor(R.attr.appSurface1),
                    toThumb   = requireContext().resolveAttrColor(R.attr.appSurface1),
                    fromTrack = requireContext().resolveAttrColor(R.attr.appPrimary),
                    toTrack   = requireContext().resolveAttrColor(R.attr.appBackground)
                )
            }
        }
        return binding.root
    }

    fun SwitchCompat.animateColors(
        @ColorInt fromThumb: Int,
        @ColorInt toThumb: Int,
        @ColorInt fromTrack: Int,
        @ColorInt toTrack: Int,
        duration: Long = 200
    ) {
        val thumb = thumbDrawable?.mutate()
        val track = trackDrawable?.mutate()
        if (thumb == null || track == null) return

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener {
                val f = it.animatedFraction
                // 簡單做一個 ARGB 插值
                fun lerpColor(from: Int, to: Int): Int {
                    val a = ((Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f).toInt())
                    val r = ((Color.red(from) + (Color.red(to) - Color.red(from)) * f).toInt())
                    val g = ((Color.green(from) + (Color.green(to) - Color.green(from)) * f).toInt())
                    val b = ((Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f).toInt())
                    return Color.argb(a, r, g, b)
                }

                thumb.setTint(lerpColor(fromThumb, toThumb))
                track.setTint(lerpColor(fromTrack, toTrack))
                invalidate()
            }
        }
        animator.start()
    }
}