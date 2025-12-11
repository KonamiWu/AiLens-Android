package com.konami.ailens.setting

import android.os.Bundle
import android.text.style.BulletSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentPowerSavingBinding

class PowerSavingFragment: Fragment() {
    private var _binding: FragmentPowerSavingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPowerSavingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }


        binding.powerSavingSwitch.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Save power saving setting
        }

        setPowerSavingDescriptionTextView()
        setPowerLowBatteryTextView()
    }

    private fun setPowerSavingDescriptionTextView() {
        val intro = requireContext().getString(R.string.power_saving_intro)
        val bullet1 = requireContext().getString(R.string.power_saving_bullet_glasses_removed)
        val bullet2 = requireContext().getString(R.string.power_saving_bullet_mono_view)

        val gapWidthPx = dpToPx(8)

        binding.powerSavingDescriptionTextView.text = buildSpannedString {
            append(intro)
            append("\n")

            inSpans(BulletSpan(gapWidthPx)) {
                append(bullet1)
                append("\n")
            }
            inSpans(BulletSpan(gapWidthPx)) {
                append(bullet2)
            }
        }
    }

    private fun setPowerLowBatteryTextView() {
        val intro = requireContext().getString(R.string.power_saving_low_battery_intro)
        val bullet1 = requireContext().getString(R.string.power_saving_low_battery_blow)

        val gapWidthPx = dpToPx(8)

        binding.lowBatteryAlertDescriptionTextView.text = buildSpannedString {
            append(intro)
            append("\n")

            inSpans(BulletSpan(gapWidthPx)) {
                append(bullet1)
                append("\n")
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}