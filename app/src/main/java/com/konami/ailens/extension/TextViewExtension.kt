package com.konami.ailens.extension

import android.widget.TextView

/**
 * Simple cross-fade text animation.
 * Fades out -> changes text -> fades in.
 * Safe for rapid calls (will cancel previous animation first).
 */
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