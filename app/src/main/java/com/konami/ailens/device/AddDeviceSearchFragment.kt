package com.konami.ailens.device

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentAddDeviceSearchBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddDeviceSearchFragment: Fragment() {
    private lateinit var binding: FragmentAddDeviceSearchBinding
    private val viewModel: AddDeviceSearchViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAddDeviceSearchBinding.inflate(inflater, container, false)
        val rotateAnim = ObjectAnimator.ofFloat(binding.loadingCircleImageView, "rotation", 0f, 360f)
        rotateAnim.duration = 2000
        rotateAnim.repeatCount = ValueAnimator.INFINITE
        rotateAnim.interpolator = LinearInterpolator()
        rotateAnim.start()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.updateFlow.first()
                findNavController().navigate(R.id.action_AddSearchFragment_to_DeviceListFragment)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(10000)
                viewModel.stopSearch()
                findNavController().navigate(R.id.action_AddSearchFragment_to_SearchFailedFragment)
            }
        }

        return binding.root
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (enter && nextAnim != 0) {
            val animation = AnimationUtils.loadAnimation(requireContext(), nextAnim)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    viewModel.startSearch()
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })
            return animation
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Reset flag when view is destroyed
    }
}