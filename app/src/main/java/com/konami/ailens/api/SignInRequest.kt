package com.konami.ailens.api

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class SignInRequest(email: String, password: String): BaseRequest<SignInRequest.SignInResponse>(url = API.SIGN_IN, method = Method.POST) {
    @Serializable
    data class SignInResponse(
        val user: User
    ) {
        @Serializable
        data class User(
            val session: Session,
            val profile: Profile
        ) {
            @Serializable
            data class Session(
                val accessToken: String,
                val refreshToken: String,
                val expiresIn: Long,
                val expiresAt: Long,
                @SerialName("token_type") val tokenType: String,
                val email: String,
                @SerialName("user_id") val userId: String,
                val role: String
            )

            @Serializable
            data class Profile(
                val id: Int,
                @SerialName("created_at") val createdAt: String,
                @SerialName("auth_id") val authId: String,
                val name: String,
                val email: String,
                val phone: String? = null,
                val language: String? = null,
                val birthday: String? = null,
                val height: String? = null,
                val weight: String? = null,
                val gender: String? = null,
                val img: String? = null,
                @SerialName("bg_image") val bgImage: String? = null,
                val active: Boolean,
                val organizationId: Int? = null
            )
        }
    }

    init {
        val jsonString = buildJsonObject {
            put("email", JsonPrimitive(email))
            put("password", JsonPrimitive(password))
        }.toString()

        data = jsonString.toByteArray()
    }

    override fun parseResponse(statusCode: Int, response: String): SignInResponse {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(response)
    }

    override fun debug(statusCode: Int, response: String) {
        Log.e("TAG", "statusCode = $statusCode, response = $response")
    }
}