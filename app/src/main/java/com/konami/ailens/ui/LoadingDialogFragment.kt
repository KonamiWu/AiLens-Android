package com.konami.ailens.ui

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.konami.ailens.R
import com.konami.ailens.databinding.DialogLoadingBinding

class LoadingDialogFragment : DialogFragment() {

    private var _binding: DialogLoadingBinding? = null
    private val binding: DialogLoadingBinding? get() = _binding

    private var spinAnimator: ObjectAnimator? = null

    private var isDismissing = false
    private var completions = mutableListOf<() -> Unit>()
    private var completionsFired = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.AiLensLoadingDialog)

        _binding = DialogLoadingBinding.inflate(LayoutInflater.from(context))
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(requireNotNull(_binding).root)

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(false)
        isCancelable = false

        binding?.main?.apply {
            alpha = 0f
            scaleX = 0.92f
            scaleY = 0.92f
        }

        return dialog
    }

    override fun onStart() {
        super.onStart()

        startSpinner()

        binding?.main?.post { playEnterAnim() }
    }

    override fun onStop() {
        stopSpinner()
        super.onStop()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    fun dismiss(completion: (() -> Unit)?) {
        if (completion != null) completions.add(completion)

        if (!isAdded) {
            fireCompletionsIfNeeded()
            return
        }

        if (isDismissing) return
        isDismissing = true

        val main = binding?.main
        if (main == null) {
            safeDismiss()
            return
        }

        main.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(220L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { safeDismiss() }
            .start()
    }

    override fun dismiss() {
        dismiss(completion = null)
    }

    override fun dismissAllowingStateLoss() {
        dismiss(completion = null)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        fireCompletionsIfNeeded()
    }

    // ----------------------------
    // Private
    // ----------------------------

    private fun playEnterAnim() {
        val main = binding?.main ?: return
        main.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun startSpinner() {
        val iv = binding?.loadingImageView ?: return
        if (spinAnimator?.isRunning == true) return

        spinAnimator = ObjectAnimator.ofFloat(iv, View.ROTATION, 0f, 360f).apply {
            duration = 900L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopSpinner() {
        spinAnimator?.cancel()
        spinAnimator = null
        binding?.loadingImageView?.rotation = 0f
    }

    private fun safeDismiss() {
        val fm = parentFragmentManager
        if (!isAdded) {
            fireCompletionsIfNeeded()
            return
        }

        if (fm.isStateSaved) {
            super.dismissAllowingStateLoss()
        } else {
            super.dismiss()
        }
    }

    private fun fireCompletionsIfNeeded() {
        if (completionsFired) return
        completionsFired = true

        val list = completions.toList()
        completions.clear()
        list.forEach { runCatching { it() } }
    }

    companion object {
        private const val TAG = "AiLensLoading"

        fun show(activity: FragmentActivity) = show(activity.supportFragmentManager)

        fun show(fm: FragmentManager) {
            if (fm.isStateSaved) return
            if (fm.findFragmentByTag(TAG) != null) return
            LoadingDialogFragment().show(fm, TAG)
        }

        fun dismiss(activity: FragmentActivity, completion: (() -> Unit)? = null) =
            dismiss(activity.supportFragmentManager, completion)

        fun dismiss(fm: FragmentManager, completion: (() -> Unit)? = null) {
            val frag = fm.findFragmentByTag(TAG) as? LoadingDialogFragment
            if (frag == null) {
                completion?.invoke()
                return
            }
            frag.dismiss(completion)
        }
    }
}
