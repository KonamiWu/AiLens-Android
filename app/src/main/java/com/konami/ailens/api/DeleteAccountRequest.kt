package com.konami.ailens.api

import android.util.Log
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class DeleteAccountRequest: SimpleStatusRequest(API.DELETE_ACCOUNT, Method.POST) {
    class DeleteAccountException(message: String) : Exception(message)

    init {
        val jsonString = buildJsonObject {
            put("userId", JsonPrimitive(SessionManager.getUserId()))
        }.toString()

        data = jsonString.toByteArray()
    }

    override fun parseResponse(statusCode: Int, response: String): Boolean {
        if (statusCode != 200) {
            throw DeleteAccountException("Delete AccountException Error")
        }
        return super.parseResponse(statusCode, response)
    }

    override fun debug(statusCode: Int, response: String) {
        Log.e("TAG", "response = $response")
    }
}