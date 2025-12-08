package com.konami.ailens.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.konami.ailens.R
import com.konami.ailens.databinding.DialogLoadingBinding

class LoadingDialogFragment : DialogFragment() {

    private var _binding: DialogLoadingBinding? = null
    private val binding get() = _binding!!

    private val animationDuration = 350L
    private val initialScale = 0.8f
    private val initialAlpha = 0.2f

    private var isAnimatingDismiss = false
    private var rotateAnimation: Animation? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use a style that keeps default dim
        val dialog = Dialog(requireContext(), R.style.AiLensLoading)
        _binding = DialogLoadingBinding.inflate(LayoutInflater.from(context))

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(false)
        isCancelable = false

        setInitialAnimationState()

        return dialog
    }

    override fun onStart() {
        super.onStart()
        
        binding.loadingImageView.doOnLayout {
            animateShow()
        }
    }

    override fun onStop() {
        binding.main.clearAnimation()
        rotateAnimation = null
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setInitialAnimationState() {
        binding.main.scaleX = initialScale
        binding.main.scaleY = initialScale
        binding.main.alpha = initialAlpha
    }

    private fun startRotateIfNeeded() {
        if (rotateAnimation == null) {
            rotateAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.rotate_loading)
        }
        binding.loadingImageView.startAnimation(rotateAnimation)
    }

    private fun animateShow() {
        binding.main.pivotX = binding.main.width / 2f
        binding.main.pivotY = binding.main.height / 2f

        val alpha = ObjectAnimator.ofFloat(binding.main, View.ALPHA, initialAlpha, 1f)
        val scaleX = ObjectAnimator.ofFloat(binding.main, View.SCALE_X, initialScale, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.main, View.SCALE_Y, initialScale, 1f)

        AnimatorSet().apply {
            playTogether(alpha, scaleX, scaleY)
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    startRotateIfNeeded()
                }
            })
            start()
        }
    }

    private fun animateDismiss(onEnd: () -> Unit) {
        binding.main.pivotX = binding.main.width / 2f
        binding.main.pivotY = binding.main.height / 2f

        val alpha = ObjectAnimator.ofFloat(binding.main, View.ALPHA, 1f, 0f)
        val scaleX = ObjectAnimator.ofFloat(binding.main, View.SCALE_X, 1f, initialScale)
        val scaleY = ObjectAnimator.ofFloat(binding.main, View.SCALE_Y, 1f, initialScale)

        AnimatorSet().apply {
            playTogether(alpha, scaleX, scaleY)
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.main.clearAnimation()
                    rotateAnimation = null
                    onEnd()
                }
            })
            start()
        }
    }

    override fun dismiss() {
        dismissWithAnimation()
    }

    override fun dismissAllowingStateLoss() {
        dismissWithAnimation(allowStateLoss = true)
    }

    private fun dismissWithAnimation(allowStateLoss: Boolean = false) {
        if (isAnimatingDismiss) return
        if (_binding == null) {
            if (allowStateLoss) super.dismissAllowingStateLoss() else super.dismiss()
            return
        }

        isAnimatingDismiss = true
        animateDismiss {
            isAnimatingDismiss = false
            if (allowStateLoss) super.dismissAllowingStateLoss() else super.dismiss()
        }
    }

    companion object {
        private const val TAG = "AiLensLoading"

        fun show(activity: FragmentActivity) {
            if (activity.supportFragmentManager.findFragmentByTag(TAG) != null) return
            LoadingDialogFragment().show(activity.supportFragmentManager, TAG)
        }

        fun dismiss(activity: FragmentActivity) {
            (activity.supportFragmentManager.findFragmentByTag(TAG) as? LoadingDialogFragment)
                ?.dismissAllowingStateLoss()
        }
    }
}
