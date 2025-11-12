package com.konami.ailens.extension

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.doOnLayout

// Unique key for storing ghost view in TextView.tag
private val CROSSFADE_GHOST_TAG_ID = View.generateViewId()

/**
 * Cross-fade text using an overlay ghost view.
 * Safe for rapid calls (will cancel and clean up previous animation/ghost).
 */
fun TextView.crossfadeOverlay(
    newText: CharSequence,
    duration: Long = 600L
) {
    // --- Always clean up previous state first ---
    // Cancel any ongoing alpha animation on this TextView
    animate().cancel()
    // Remove previous ghost (if any)
    clearCrossfadeGhost()

    // 如果文字已經一樣，就只把自己 alpha 拉回來，避免卡在半透明
    if (text == newText) {
        alpha = 1f
        return
    }

    val parentView = parent as? ViewGroup
    if (parentView == null) {
        // No parent -> fallback to simple version
        crossfadeText(newText, duration)
        return
    }

    if (width == 0 || height == 0) {
        // Not laid out yet -> wait until layout finished
        doOnLayout { crossfadeOverlay(newText, duration) }
        return
    }

    // --- (Optional) measure new text height; currently just informational ---
    run {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        val measureView = TextView(context).apply {
            text = newText
            setTextColor(this@crossfadeOverlay.textColors)
            typeface = this@crossfadeOverlay.typeface
            setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_PX,
                this@crossfadeOverlay.textSize
            )
            gravity = this@crossfadeOverlay.gravity
            maxLines = this@crossfadeOverlay.maxLines
            ellipsize = this@crossfadeOverlay.ellipsize
        }

        measureView.measure(widthSpec, heightSpec)
        val newHeight = measureView.measuredHeight
        val oldHeight = height
        val heightChanged = newHeight != oldHeight
        // You can use `heightChanged` to switch strategy if needed,
        // but DO NOT change layoutParams here to avoid jumping.
    }

    // --- Create ghost view with "old text" on parent overlay ---
    val ghost = TextView(context).apply {
        text = this@crossfadeOverlay.text
        setTextColor(this@crossfadeOverlay.textColors)
        typeface = this@crossfadeOverlay.typeface
        setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_PX,
            this@crossfadeOverlay.textSize
        )
        gravity = this@crossfadeOverlay.gravity
        maxLines = this@crossfadeOverlay.maxLines
        ellipsize = this@crossfadeOverlay.ellipsize
        alpha = 1f
    }

    // Keep reference so we can cancel/remove on next call
    setTag(CROSSFADE_GHOST_TAG_ID, ghost)

    parentView.overlay.add(ghost)

    ghost.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
    )
    ghost.layout(left, top, right, bottom)

    // --- Set new text on self, fade in; ghost fades out ---
    val targetAlpha = 1f
    alpha = 0f
    text = newText

    // Fade out ghost
    ghost.animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction {
            // Remove only if this ghost is still the current one
            val currentGhost = getTag(CROSSFADE_GHOST_TAG_ID) as? View
            if (currentGhost === ghost) {
                (parent as? ViewGroup)?.overlay?.remove(ghost)
                setTag(CROSSFADE_GHOST_TAG_ID, null)
            } else {
                // 如果中途又換了一個新的 ghost，這個舊的就當作保險再移除一次
                (ghost.parent as? ViewGroup)?.overlay?.remove(ghost)
            }
        }
        .start()

    // Fade in new text (this TextView)
    animate()
        .alpha(targetAlpha)
        .setDuration(duration)
        .start()
}

/**
 * Simple version: fade out -> change text -> fade in.
 * Also safe for rapid calls (will cancel previous animation first).
 */
fun TextView.crossfadeText(newText: CharSequence, duration: Long = 150L) {
    // Cancel previous animation and remove any ghost just in case
    animate().cancel()
    clearCrossfadeGhost()

    if (text == newText) {
        alpha = 1f
        return
    }

    animate()
        .alpha(0f)
        .setDuration(duration)
        .withEndAction {
            text = newText
            animate()
                .alpha(1f)
                .setDuration(duration)
                .start()
        }
        .start()
}

/**
 * Remove ghost view (if any) from overlay and clear tag.
 */
private fun TextView.clearCrossfadeGhost() {
    val ghost = getTag(CROSSFADE_GHOST_TAG_ID) as? View ?: return

    // Cancel its animation
    ghost.animate().cancel()

    // Try to remove from current parent overlay
    (parent as? ViewGroup)?.overlay?.remove(ghost)

    // Just in case it stayed attached elsewhere
    (ghost.parent as? ViewGroup)?.overlay?.remove(ghost)

    setTag(CROSSFADE_GHOST_TAG_ID, null)
}
