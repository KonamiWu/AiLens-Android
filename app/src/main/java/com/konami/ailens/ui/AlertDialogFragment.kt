package com.konami.ailens.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.konami.ailens.R
import com.konami.ailens.databinding.DialogAlertBinding
import androidx.fragment.app.FragmentActivity

class AlertDialogFragment : DialogFragment() {

    private var _binding: DialogAlertBinding? = null
    private val binding get() = _binding!!

    private val animationDuration = 350L
    private val initialScale = 0.8f
    private val initialAlpha = 0.2f

    private var positiveAction: (() -> Unit)? = null
    private var negativeAction: (() -> Unit)? = null

    private var isAnimatingDismiss = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.AiLensAlertDialog)
        _binding = DialogAlertBinding.inflate(LayoutInflater.from(context))

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.setCancelable(false)
        isCancelable = false

        setupViews()
        setInitialAnimationState()

        return dialog
    }

    override fun onStart() {
        super.onStart()

        binding.contentLayout.doOnLayout {
            animateShow()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupViews() {
        val title = requireArguments().getString(ARG_TITLE).orEmpty()
        val message = requireArguments().getString(ARG_MESSAGE)
        val positiveTitle = requireArguments().getString(ARG_POSITIVE_TITLE)
        val negativeTitle = requireArguments().getString(ARG_NEGATIVE_TITLE)

        binding.titleTextView.text = title

        if (message != null) {
            binding.messageTextView.text = message
            binding.messageTextView.isVisible = true
        } else {
            binding.messageTextView.isVisible = false
        }

        if (positiveTitle != null) {
            binding.positiveButton.text = positiveTitle
            binding.positiveButton.isVisible = true
            binding.positiveButton.setOnClickListener {
                positiveAction?.invoke() ?: dismiss()
            }
        } else {
            binding.positiveButton.isVisible = false
        }

        if (negativeTitle != null) {
            binding.negativeButton.text = negativeTitle
            binding.negativeButton.isVisible = true
            binding.negativeButton.setOnClickListener {
                negativeAction?.invoke() ?: dismiss()
            }
        } else {
            binding.negativeButton.isVisible = false
        }

        binding.buttonSeparator.isVisible = positiveTitle != null && negativeTitle != null

        // If no buttons, tap outside to dismiss
        if (positiveTitle == null && negativeTitle == null) {
            binding.main.setOnClickListener {
                dismiss()
            }
            binding.contentLayout.setOnClickListener { /* Prevent click propagation */ }
        } else {
            binding.main.isClickable = false
            binding.main.setOnClickListener(null)
            binding.contentLayout.setOnClickListener(null)
        }
    }

    private fun setInitialAnimationState() {
        binding.main.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.contentLayout.scaleX = initialScale
        binding.contentLayout.scaleY = initialScale
        binding.contentLayout.alpha = initialAlpha
    }

    private fun animateShow() {
        binding.contentLayout.pivotX = binding.contentLayout.width / 2f
        binding.contentLayout.pivotY = binding.contentLayout.height / 2f

        val alpha = ObjectAnimator.ofFloat(binding.contentLayout, View.ALPHA, initialAlpha, 1f)
        val scaleX = ObjectAnimator.ofFloat(binding.contentLayout, View.SCALE_X, initialScale, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.contentLayout, View.SCALE_Y, initialScale, 1f)

        AnimatorSet().apply {
            playTogether(alpha, scaleX, scaleY)
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun animateDismiss(onEnd: () -> Unit) {
        binding.contentLayout.pivotX = binding.contentLayout.width / 2f
        binding.contentLayout.pivotY = binding.contentLayout.height / 2f

        val alpha = ObjectAnimator.ofFloat(binding.contentLayout, View.ALPHA, 1f, 0f)
        val scaleX = ObjectAnimator.ofFloat(binding.contentLayout, View.SCALE_X, 1f, initialScale)
        val scaleY = ObjectAnimator.ofFloat(binding.contentLayout, View.SCALE_Y, 1f, initialScale)

        AnimatorSet().apply {
            playTogether(alpha, scaleX, scaleY)
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    // Animate dismiss by default
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
        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"
        private const val ARG_POSITIVE_TITLE = "arg_positive_title"
        private const val ARG_NEGATIVE_TITLE = "arg_negative_title"

        fun newInstance(
            title: String,
            message: String?,
            positiveTitle: String?,
            positiveAction: (() -> Unit)?,
            negativeTitle: String?,
            negativeAction: (() -> Unit)?
        ): AlertDialogFragment {
            return AlertDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_MESSAGE to message,
                    ARG_POSITIVE_TITLE to positiveTitle,
                    ARG_NEGATIVE_TITLE to negativeTitle
                )
                this.positiveAction = positiveAction
                this.negativeAction = negativeAction
            }
        }
    }
}

class Alert private constructor(
    private val activity: FragmentActivity,
    private val title: String,
    private val message: String?,
    private val positiveTitle: String?,
    private val positiveAction: (() -> Unit)?,
    private val negativeTitle: String?,
    private val negativeAction: (() -> Unit)?
) {
    fun show() {

        val fragment = AlertDialogFragment.newInstance(
            title = title,
            message = message,
            positiveTitle = positiveTitle,
            positiveAction = positiveAction,
            negativeTitle = negativeTitle,
            negativeAction = negativeAction
        )

        // Avoid duplicate show if you want:
        val tag = "AiLensAlertDialog"
        if (activity.supportFragmentManager.findFragmentByTag(tag) == null) {
            fragment.show(activity.supportFragmentManager, tag)
        }
    }

    companion object {
        fun newAlert(
            activity: FragmentActivity,
            title: String,
            message: String? = null,
            positiveTitle: String? = null,
            positiveAction: (() -> Unit)? = null,
            negativeTitle: String? = null,
            negativeAction: (() -> Unit)? = null
        ): Alert {
            return Alert(
                activity,
                title,
                message,
                positiveTitle,
                positiveAction,
                negativeTitle,
                negativeAction
            )
        }
    }
}