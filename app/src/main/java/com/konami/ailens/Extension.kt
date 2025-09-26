package com.konami.ailens

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