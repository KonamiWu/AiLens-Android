package com.konami.ailens.setting

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.command.GetAutomaticBrightnessCommand
import com.konami.ailens.ble.command.GetBrightnessCommand
import com.konami.ailens.ble.command.GetScreenTimeoutCommand
import com.konami.ailens.ble.command.SetAutomaticBrightnessCommand
import com.konami.ailens.ble.command.SetBrightnessCommand
import com.konami.ailens.ble.command.SetScreenTimeoutCommand
import android.widget.SeekBar
import com.konami.ailens.databinding.FragmentDisplayBrightnessBinding
import com.konami.ailens.selection.SelectionFragment
import com.konami.ailens.selection.SelectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class DisplayBrightnessFragment : Fragment() {
    data class Item(val title: String, val seconds: Int) : SelectionItem {
        override val id: String get() = seconds.toString()
        override val displayText: String get() = title
    }
    private lateinit var binding: FragmentDisplayBrightnessBinding
    private val list = mutableListOf<Item>()
    private var currentTimeout = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDisplayBrightnessBinding.inflate(inflater, container, false)

        list.add(Item(title = "10%s".format(getString(R.string.display_brightness_seconds)), seconds = 10))
        list.add(Item(title = "30%s".format(getString(R.string.display_brightness_seconds)), seconds = 30))
        list.add(Item(title = "1%s".format(getString(R.string.display_brightness_minutes)), seconds = 60))
        list.add(Item(title = "3%s".format(getString(R.string.display_brightness_minutes)), seconds = 180))
        list.add(Item(title = getString(R.string.display_brightness_always_on), seconds = 0))

        val command = GetScreenTimeoutCommand()
        command.completion = { result ->
            result.onSuccess { timeout ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        currentTimeout = timeout
                        updateTimeoutDisplay(timeout)
                    }
                }
            }
        }
        BLEService.instance.connectedSession.value?.add(command)

        val automatic = GetAutomaticBrightnessCommand()
        automatic.completion = { result ->
            result.onSuccess { isOn ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        binding.automaticBrightnessSwitch.isChecked = isOn
                    }
                }
            }
        }

        BLEService.instance.connectedSession.value?.add(automatic)

        val brightness = GetBrightnessCommand()
        brightness.completion = { result ->
            result.onSuccess { level ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        binding.brightnessSlider.progress = level
                    }
                }
            }
        }

        BLEService.instance.connectedSession.value?.add(brightness)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.automaticBrightnessSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.brightnessSlider.isVisible = !isChecked
            BLEService.instance.connectedSession.value?.add(SetAutomaticBrightnessCommand(isChecked))
        }

        binding.screenTimeoutItem.setOnClickListener {
            val closestItem = list.minByOrNull { abs(it.seconds - currentTimeout) } ?: list.first()

            val dialog = SelectionFragment.newInstance(
                items = list,
                currentItem = closestItem
            ) { selectedItem ->
                val command = SetScreenTimeoutCommand(selectedItem.seconds)
                command.completion = { result ->
                    result.onSuccess {
                        viewLifecycleOwner.lifecycleScope.launch {
                            withContext(Dispatchers.Main) {
                                currentTimeout = selectedItem.seconds
                                updateTimeoutDisplay(selectedItem.seconds)
                            }
                        }
                    }
                }
                BLEService.instance.connectedSession.value?.add(command)
            }

            dialog.show(childFragmentManager, "Selection")
        }

        binding.brightnessSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val command = SetBrightnessCommand(binding.brightnessSlider.progress)
                BLEService.instance.connectedSession.value?.add(command)
            }
        })
    }

    private fun updateTimeoutDisplay(timeout: Int) {
        if (timeout == 0) {
            binding.screenTimeoutValueTextView.text = getString(R.string.display_brightness_always_on)
        } else {
            val minutes = timeout / 60
            val seconds = timeout % 60
            var string = ""
            if (minutes != 0) string += "%d ".format(minutes) + getString(R.string.display_brightness_minutes)
            if (seconds != 0) string += "%d ".format(seconds) + getString(R.string.display_brightness_seconds)
            binding.screenTimeoutValueTextView.text = string.trim()
        }
    }
}
