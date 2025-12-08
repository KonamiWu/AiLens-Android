package com.konami.ailens.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class SignUpRequest(email: String, password: String, name: String): SimpleStatusRequest(url = API.SIGN_UP, method = Method.POST) {
    class SignUpException(message: String) : Exception(message)

    init {
        needAuthenticate = false
        val jsonString = buildJsonObject {
            put("email", JsonPrimitive(email))
            put("password", JsonPrimitive(password))
            put("name", JsonPrimitive(name))
        }.toString()

        data = jsonString.toByteArray()
    }
}