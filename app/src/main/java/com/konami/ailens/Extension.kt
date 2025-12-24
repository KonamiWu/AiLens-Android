package com.konami.ailens

import android.content.Context
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

fun ByteArray.startsWith(hexPrefix: String): Boolean {
    val prefixBytes = hexPrefix.hexToByteArray()
    if (this.size < prefixBytes.size) {
        return false
    }
    return prefixBytes.indices.all { i ->
        this[i] == prefixBytes[i]
    }
}

fun String.hexToByteArray(): ByteArray {
    val cleaned = this.replace(" ", "").replace(":", "").replace("-", "")

    require(cleaned.length % 2 == 0) { "Hex string must have even length" }

    return ByteArray(cleaned.length / 2) { i ->
        val index = i * 2
        cleaned.substring(index, index + 2).toInt(16).toByte()
    }
}

fun Context.resolveAttrColor(attrResId: Int, alpha: Float = 1f): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrResId, typedValue, true)
    val color = if (typedValue.resourceId != 0) {
        ContextCompat.getColor(this, typedValue.resourceId)
    } else {
        typedValue.data
    }

    return ColorUtils.setAlphaComponent(color, (255 * alpha).toInt())
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density