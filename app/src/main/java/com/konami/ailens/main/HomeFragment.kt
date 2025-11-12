package com.konami.ailens.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.Glasses
import com.konami.ailens.databinding.FragmentHomeBinding
import com.konami.ailens.navigation.NavigationService
import kotlinx.coroutines.launch
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.konami.ailens.resolveAttrColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("MissingPermission")
class HomeFragment: Fragment() {
    private lateinit var binding: FragmentHomeBinding


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.navigationButton.setOnClickListener {
            navigateToNavigationScreen()
        }

        binding.settingButton.setOnClickListener {
            findNavController().navigate(R.id.action_HomeFragment_to_SettingFragment)
        }

         binding.teleprompterButton.setOnClickListener {
         }

         binding.translationButton.setOnClickListener {
             findNavController().navigate(R.id.action_HomeFragment_to_SmartTranslationFragment)
         }

        // Monitor connectedSession state changes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                BLEService.instance.connectedSession
                    .flatMapLatest { session ->
                        session?.state?.map { state -> session to state } ?: flowOf(null to null)  // (session, state)
                    }
                    .collect { (session, state) ->
                        if (session == null) {
                            setViewStateDisconnected()
                        } else {
                            binding.nameTextView.text = session.device.name
                            when (state) {
                                Glasses.State.CONNECTED -> setViewStateConnected()
                                else -> setViewStateDisconnected()
                            }
                        }
                    }
            }
        }

        // Monitor battery level separately
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                BLEService.instance.connectedSession
                    .flatMapLatest { session ->
                        session?.batteryFlow ?: flowOf()
                    }
                    .collect { (batteryLevel, isCharging) ->
                        if (batteryLevel > 0) {
                            binding.batteryImageView.visibility = View.VISIBLE
                            binding.batteryTextView.text = "%d%%".format(batteryLevel)
                            if (batteryLevel <= 20)
                                binding.batteryTextView.setTextColor(requireContext().resolveAttrColor(R.attr.appRed))
                            else
                                binding.batteryTextView.setTextColor(requireContext().resolveAttrColor(R.attr.appBorderLightGray))
                            if (isCharging)
                                binding.batteryImageView.setImageResource(R.drawable.ic_charging)
                            else
                                setBatteryImage(batteryLevel)
                        }
                    }
            }
        }

        setViewStateDisconnectedWithoutAnimation()
        return binding.root
    }

    private fun setBatteryImage(level: Int) {
        if (level <= 20)
            binding.batteryImageView.setImageResource(R.drawable.ic_battery_0_20)
        else if (level < 60)
            binding.batteryImageView.setImageResource(R.drawable.ic_battery_21_59)
        else if (level < 100)
            binding.batteryImageView.setImageResource(R.drawable.ic_battery_60_99)
        else
            binding.batteryImageView.setImageResource(R.drawable.ic_battery_100)
    }
    
    private fun navigateToNavigationScreen() {
        val isNavigating = NavigationService.instance.isNavigating
        if (isNavigating) {
            findNavController().navigate(R.id.action_HomeFragment_to_NavigationFragment)
        } else {
            findNavController().navigate(R.id.action_HomeFragment_to_AddressPickerFragment)
        }
    }

    private fun setViewStateConnected() {
        // Fade out disconnected views
        fadeOut(binding.infoImageView)
        fadeOut(binding.diconnectedTextView)
        fadeOut(binding.disconnectLayout)

        // Fade in connected views
        fadeIn(binding.functionLayout)
        fadeIn(binding.batteryImageView)
        fadeIn(binding.batteryTextView)
        binding.glassesImageView.animate().alpha(1f).setDuration(300).start()
    }

    private fun setViewStateDisconnected() {
        // Fade out connected views
        fadeOut(binding.functionLayout)
        fadeOut(binding.batteryImageView)
        fadeOut(binding.batteryTextView)

        // Fade in disconnected views
        fadeIn(binding.infoImageView)
        fadeIn(binding.diconnectedTextView)
        fadeIn(binding.disconnectLayout)
        binding.glassesImageView.animate().alpha(0.5f).setDuration(300).start()
    }

    private fun setViewStateDisconnectedWithoutAnimation() {
        binding.functionLayout.visibility = View.INVISIBLE
        binding.batteryImageView.visibility = View.INVISIBLE
        binding.batteryTextView.visibility = View.INVISIBLE
        binding.infoImageView.visibility = View.INVISIBLE
        binding.diconnectedTextView.visibility = View.INVISIBLE
        binding.disconnectLayout.visibility = View.INVISIBLE
        binding.glassesImageView.alpha = 0.5f
    }

    private fun fadeIn(view: View, duration: Long = 300) {
        if (view.isVisible && view.alpha == 1f) return

        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setListener(null)
            .start()
    }

    private fun fadeOut(view: View, duration: Long = 300) {
        if (view.isInvisible && view.alpha == 0f) return

        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                view.visibility = View.INVISIBLE
            }
            .start()
    }
}