package com.konami.ailens.orchestrator.capability

import kotlinx.serialization.Serializable

@Serializable
enum class Operation(val raw: String) {
    GET("get"),
    SET("set");

    companion object {
        fun fromRaw(value: String?): Operation? {
            return entries.firstOrNull { it.raw.equals(value, ignoreCase = true) }
        }
    }
}