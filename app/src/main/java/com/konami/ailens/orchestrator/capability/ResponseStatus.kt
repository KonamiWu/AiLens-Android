package com.konami.ailens.orchestrator.capability

import kotlinx.serialization.Serializable

@Serializable
enum class ResponseStatus {
    ok, error
}