package com.konami.ailens.translation.interpretation

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentInterpretationMainBinding
import com.konami.ailens.extension.crossfadeOverlay
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.resolveAttrColor
import com.konami.ailens.translation.LanguageSelectionFragment
import kotlin.math.PI

class InterpretationMainFragment: Fragment() {
    private lateinit var binding: FragmentInterpretationMainBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentInterpretationMainBinding.inflate(inflater, container, false)
        binding.bilingualSwitch.setOnCheckedChangeListener { _, isChecked ->
            Orchestrator.instance.bilingual = isChecked
            if (isChecked) {
                binding.bilingualSwitch.animateColors(
                    fromThumb = requireContext().resolveAttrColor(R.attr.appSurface1),
                    toThumb   = requireContext().resolveAttrColor(R.attr.appSurface1),
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

        updateLanguageText(false)

        binding.exchangeButton.setOnClickListener {
            binding.exchangeButton.isEnabled = false
            binding.exchangeImageView.animate().rotationBy(180f).scaleX(1.4f).scaleY(1.4f).setDuration(300).setInterpolator(LinearInterpolator()).withEndAction {
                binding.exchangeImageView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotationBy(180f)
                    .setDuration(300)
                    .setInterpolator(LinearInterpolator()).withEndAction { binding.exchangeButton.isEnabled = true }
                    .start()
            }.start()
            val temp = Orchestrator.instance.interpretationSourceLanguage
            Orchestrator.instance.interpretationSourceLanguage = Orchestrator.instance.interpretationTargetLanguage
            Orchestrator.instance.interpretationTargetLanguage = temp
            updateLanguageText(true)
        }

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.bilingualSwitch.isChecked = Orchestrator.instance.bilingual

        binding.sourceButton.setOnClickListener {
            showLanguageSelection(true)
        }

        binding.targetButton.setOnClickListener {
            showLanguageSelection(false)
        }

        return binding.root
    }

    private fun updateLanguageText(animated: Boolean) {
        if (animated) {
            binding.sourceTextView.crossfadeOverlay(Orchestrator.instance.interpretationSourceLanguage.title)
            binding.targetTextView.crossfadeOverlay(Orchestrator.instance.interpretationTargetLanguage.title)
        } else {
            binding.sourceTextView.text = Orchestrator.instance.interpretationSourceLanguage.title
            binding.targetTextView.text = Orchestrator.instance.interpretationTargetLanguage.title
        }
    }

    private fun showLanguageSelection(isSource: Boolean) {
        val currentLanguage = if (isSource) {
            Orchestrator.instance.interpretationSourceLanguage
        } else {
            Orchestrator.instance.interpretationTargetLanguage
        }

        val dialog = LanguageSelectionFragment.newInstance(currentLanguage) { selectedLanguage ->
            if (isSource) {
                Orchestrator.instance.interpretationSourceLanguage = selectedLanguage
            } else {
                Orchestrator.instance.interpretationTargetLanguage = selectedLanguage
            }
            updateLanguageText(true)
        }

        dialog.show(childFragmentManager, "LanguageSelection")
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
