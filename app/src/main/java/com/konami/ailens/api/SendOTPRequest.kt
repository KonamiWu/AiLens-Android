package com.konami.ailens.api

import android.util.Log
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class SendOTPRequest(email: String): SimpleStatusRequest(API.SEND_OTP, Method.POST) {
    init {
        needAuthenticate =  false
        val jsonString = buildJsonObject {
            put("email", JsonPrimitive(email))
        }.toString()

        data = jsonString.toByteArray()
    }
}