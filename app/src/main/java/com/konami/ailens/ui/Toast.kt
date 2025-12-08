package com.konami.ailens.ui

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.konami.ailens.R
import com.konami.ailens.databinding.ViewToastNotificationBinding
import com.konami.ailens.resolveAttrColor

object Toast {
    enum class Style {
        SUCCESS,
        ERROR,
        INFO;

        val icon: Int
            get() = when (this) {
                SUCCESS -> R.drawable.ic_toast_success
                ERROR -> R.drawable.ic_toast_success
                INFO -> R.drawable.ic_toast_success
            }
    }

    enum class Duration(val millis: Long) {
        SHORT(2000),
        LONG(4000)
    }

    private val activeToasts = mutableListOf<View>()
    fun show(activity: Activity, message: String, style: Style = Style.INFO, duration: Duration = Duration.SHORT) {
        val binding = ViewToastNotificationBinding.inflate(LayoutInflater.from(activity))

        binding.iconImageView.setImageResource(style.icon)
        binding.messageTextView.text = message

        val decorView = activity.window.decorView as ViewGroup
        val rootView = decorView.findViewById<FrameLayout>(android.R.id.content)

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        rootView.addView(binding.root, layoutParams)
        activeToasts.add(binding.root)

        val windowInsets = ViewCompat.getRootWindowInsets(binding.root)
        val statusBarHeight = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
        val finalTopPosition = statusBarHeight + 8f
        binding.containerView.doOnLayout {
            binding.root.translationY = -binding.containerView.height.toFloat()
            binding.root.animate()
                .translationY(finalTopPosition)
                .setDuration(500)
                .withEndAction {
                    binding.root.postDelayed({
                        binding.root.animate()
                            .translationY(-binding.root.height.toFloat())
                            .setDuration(500)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                rootView.removeView(binding.root)
                                activeToasts.remove(binding.root)
                            }
                            .start()
                    }, duration.millis)
                }.start()
        }




    }

    fun dismissAll(activity: Activity) {
        val decorView = activity.window.decorView as ViewGroup
        val rootView = decorView.findViewById<FrameLayout>(android.R.id.content)

        activeToasts.toList().forEach { toast ->
            rootView.removeView(toast)
        }
        activeToasts.clear()
    }
}
