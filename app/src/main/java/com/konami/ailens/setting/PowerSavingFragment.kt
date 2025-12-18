package com.konami.ailens.setting

import android.os.Bundle
import android.text.style.BulletSpan
import android.util.Log
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
import com.konami.ailens.ble.command.GetLowBatteryAlertCommand
import com.konami.ailens.ble.command.GetPowerSavingCommand
import com.konami.ailens.ble.command.SetLowBatteryAlertCommand
import com.konami.ailens.ble.command.SetPowerSavingCommand
import com.konami.ailens.databinding.FragmentPowerSavingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PowerSavingFragment : Fragment() {
    private lateinit var binding: FragmentPowerSavingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPowerSavingBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.powerSavingSwitch.setOnCheckedChangeListener { _, isChecked ->
            BLEService.instance.connectedSession.value?.add(SetPowerSavingCommand(isChecked))
        }

        binding.lowBatteryAlertSwitch.setOnCheckedChangeListener { _, isChecked ->
            BLEService.instance.connectedSession.value?.add(SetLowBatteryAlertCommand(isChecked))
        }

        setPowerSavingDescriptionTextView()
        setPowerLowBatteryTextView()

        val powerSavingCommand = GetPowerSavingCommand()
        powerSavingCommand.completion = { result ->
            result.onSuccess { isOn ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        binding.powerSavingSwitch.isChecked = isOn
                    }
                }
            }
        }

        val lowBatteryAlertCommand = GetLowBatteryAlertCommand()
        lowBatteryAlertCommand.completion = { result ->
            result.onSuccess { isOn ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        binding.lowBatteryAlertSwitch.isChecked = isOn
                    }
                }
            }
        }

        BLEService.instance.connectedSession.value?.add(powerSavingCommand)
        BLEService.instance.connectedSession.value?.add(lowBatteryAlertCommand)
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
}