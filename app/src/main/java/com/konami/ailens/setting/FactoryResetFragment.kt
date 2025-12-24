package com.konami.ailens.setting

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.os.Bundle
import android.text.style.BulletSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.command.FactoryResetCommand
import com.konami.ailens.databinding.FragmentFactoryResetBinding
import com.konami.ailens.resolveAttrColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FactoryResetFragment: Fragment() {
    private lateinit var binding: FragmentFactoryResetBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentFactoryResetBinding.inflate(inflater, container, false)

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.resetButton.isEnabled = false

        binding.resetButton.setOnClickListener {
            BLEService.instance.connectedSession.value?.add(FactoryResetCommand())
            findNavController().popBackStack()
        }

        setFactoryResetDescription1TextView()
        setFactoryResetDescription2TextView()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startCountdown()
    }

    private fun startCountdown() {
        viewLifecycleOwner.lifecycleScope.launch {
            val baseText = requireContext().getString(R.string.factory_reset_read_notice)
            for (seconds in 10 downTo 1) {
                binding.resetButton.text = "$baseText ($seconds)"
                delay(1000)
            }
            setResetButtonEnable()
            binding.resetButton.text = requireContext().getString(R.string.factory_reset)
            binding.resetButton.isEnabled = true
        }
    }

    private fun setFactoryResetDescription1TextView() {
        val content1 = requireContext().getString(R.string.factory_reset_title_1_content_1)
        val content2 = requireContext().getString(R.string.factory_reset_title_1_content_2)
        val content3 = requireContext().getString(R.string.factory_reset_title_1_content_3)

        val gapWidthPx = dpToPx(8)

        binding.factoryResetDescription1TextView.text = buildSpannedString {
            inSpans(BulletSpan(gapWidthPx)) {
                append(content1)
                append("\n")
            }
            inSpans(BulletSpan(gapWidthPx)) {
                append(content2)
                append("\n")
            }
            inSpans(BulletSpan(gapWidthPx)) {
                append(content3)
            }
        }
    }

    private fun setFactoryResetDescription2TextView() {
        val content1 = requireContext().getString(R.string.factory_reset_title_2_content_1)
        val content2 = requireContext().getString(R.string.factory_reset_title_2_content_2)
        val content3 = requireContext().getString(R.string.factory_reset_title_2_content_3)

        val gapWidthPx = dpToPx(8)

        binding.factoryResetDescription2TextView.text = buildSpannedString {
            inSpans(BulletSpan(gapWidthPx)) {
                append(content1)
                append("\n")
            }
            inSpans(BulletSpan(gapWidthPx)) {
                append(content2)
                append("\n")
            }
            inSpans(BulletSpan(gapWidthPx)) {
                append(content3)
            }
        }
    }

    private fun setResetButtonEnable() {
        val fromFillColor = requireContext().resolveAttrColor(R.attr.appButtonDisable)
        val toFillColor = requireContext().resolveAttrColor(R.attr.appPrimary)
        val fromTextColor = requireContext().resolveAttrColor(R.attr.appTextDisable)
        val toTextColor = requireContext().resolveAttrColor(R.attr.appTextButton)

        val fillColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromFillColor, toFillColor)
        fillColorAnimator.duration = 300
        fillColorAnimator.addUpdateListener { animator ->
            binding.resetButton.fillColor = animator.animatedValue as Int
        }

        val textColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromTextColor, toTextColor)
        textColorAnimator.duration = 300
        textColorAnimator.addUpdateListener { animator ->
            binding.resetButton.setTextColor(animator.animatedValue as Int)
        }

        fillColorAnimator.start()
        textColorAnimator.start()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}