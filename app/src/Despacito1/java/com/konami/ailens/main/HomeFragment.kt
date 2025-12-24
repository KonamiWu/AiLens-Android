package com.konami.ailens.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentHomeBinding
import com.konami.ailens.view.BlurBorderView

@SuppressLint("MissingPermission")
class HomeFragment: Fragment(), TabBarConfigurable {
    private lateinit var binding: FragmentHomeBinding
    private var blurUpdateRunnable: Runnable? = null

    override fun shouldShowTabBar(): Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        binding.settingLayout.setOnClickListener {
            findNavController().navigate(R.id.action_HomeFragment_to_SettingFragment)
        }
//
//         binding.teleprompterButton.setOnClickListener {
//         }
//
//         binding.translationButton.setOnClickListener {
//             findNavController().navigate(R.id.action_HomeFragment_to_SmartTranslationFragment)
//         }
//
//        // Monitor connectedSession state changes
//        viewLifecycleOwner.lifecycleScope.launch {
//            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                BLEService.instance.connectedSession
//                    .flatMapLatest { session ->
//                        session?.state?.map { state -> session to state } ?: flowOf(null to null)  // (session, state)
//                    }
//                    .collect { (session, state) ->
//                        if (session == null) {
//                            setViewStateDisconnected()
//                        } else {
//                            when (state) {
//                                Glasses.State.CONNECTED -> setViewStateConnected()
//                                else -> setViewStateDisconnected()
//                            }
//                        }
//                    }
//            }
//        }
//
//        // Monitor battery level separately
//        viewLifecycleOwner.lifecycleScope.launch {
//            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                BLEService.instance.connectedSession
//                    .flatMapLatest { session ->
//                        session?.batteryFlow ?: flowOf()
//                    }
//                    .collect { (batteryLevel, isCharging) ->
//                        if (batteryLevel > 0) {
//                            binding.batteryImageView.visibility = View.VISIBLE
//                            binding.batteryTextView.text = "%d%%".format(batteryLevel)
//                            if (batteryLevel <= 20)
//                                binding.batteryTextView.setTextColor(requireContext().resolveAttrColor(R.attr.appRed))
//                            else
//                                binding.batteryTextView.setTextColor(requireContext().resolveAttrColor(R.attr.appBorderLightGray))
//                            if (isCharging)
//                                binding.batteryImageView.setImageResource(R.drawable.ic_charging)
//                            else
//                                setBatteryImage(batteryLevel)
//                        }
//                    }
//            }
//        }
//
//        setViewStateDisconnectedWithoutAnimation()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        clearAllBlurCache()
        setBlurCacheFrozen(false)
    }

    override fun onPause() {
        super.onPause()
        blurUpdateRunnable?.let { binding.root.removeCallbacks(it) }
        setBlurCacheFrozen(true)
    }

    override fun onResume() {
        super.onResume()
        blurUpdateRunnable = Runnable {
            requestBlurUpdate()
        }
        binding.root.postDelayed(blurUpdateRunnable!!, 100)
    }

    private fun setBlurCacheFrozen(frozen: Boolean) {
        fun freezeBlurInView(view: View) {
            if (view is BlurBorderView) {
                view.isCacheFrozen = frozen
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    freezeBlurInView(view.getChildAt(i))
                }
            }
        }
        freezeBlurInView(binding.root)
    }

    private fun clearAllBlurCache() {
        fun clearCacheInView(view: View) {
            if (view is BlurBorderView) {
                view.clearCache()
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    clearCacheInView(view.getChildAt(i))
                }
            }
        }
        clearCacheInView(binding.root)
    }

    private fun requestBlurUpdate() {
        fun updateBlurInView(view: View) {
            if (view is BlurBorderView) {
                view.refreshBlur()
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    updateBlurInView(view.getChildAt(i))
                }
            }
        }
        updateBlurInView(binding.root)
    }

    private fun setBatteryImage(level: Int) {
//        if (level <= 20)
//            binding.batteryImageView.setImageResource(R.drawable.ic_battery_0_20)
//        else if (level < 60)
//            binding.batteryImageView.setImageResource(R.drawable.ic_battery_21_59)
//        else if (level < 100)
//            binding.batteryImageView.setImageResource(R.drawable.ic_battery_60_99)
//        else
//            binding.batteryImageView.setImageResource(R.drawable.ic_battery_100)
    }
    

    private fun setViewStateConnected() {
    }

    private fun setViewStateDisconnected() {
    }

    private fun setViewStateDisconnectedWithoutAnimation() {
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