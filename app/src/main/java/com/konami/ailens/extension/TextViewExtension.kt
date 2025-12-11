package com.konami.ailens.extension

import android.os.Build
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.widget.TextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans


fun TextView.crossfadeOverlay(
    newText: CharSequence,
    duration: Long = 300L
) {
    // Cancel any ongoing animation
    animate().cancel()

    // If text is the same, ensure full opacity
    if (text == newText) {
        alpha = 1f
        return
    }

    // Fade out
    animate()
        .alpha(0f)
        .setDuration(duration / 2)
        .withEndAction {
            // Change text
            text = newText
            // Fade in
            animate()
                .alpha(1f)
                .setDuration(duration / 2)
                .start()
        }
        .start()
}