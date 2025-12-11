package com.konami.ailens.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class RefreshTokenRequest(token: String): BaseRequest<RefreshTokenRequest.RefreshTokenResponse>(url = API.REFRESH, method = Method.GET) {
    @Serializable
    data class RefreshTokenResponse(
        val user: User,
        val session: Session
    ) {
        @Serializable
        data class User(
            val id: String,
            val aud: String,
            val role: String,
            val email: String,
            @SerialName("email_confirmed_at") val emailConfirmedAt: String,
            val phone: String,
            @SerialName("confirmed_at") val confirmedAt: String,
            @SerialName("recovery_sent_at") val recoverySentAt: String,
            @SerialName("last_sign_in_at") val lastSignInAt: String,
            @SerialName("app_metadata") val appMetadata: AppMetadata,
            @SerialName("user_metadata") val userMetadata: UserMetadata,
            val identities: List<Identity>,
            @SerialName("created_at") val createdAt: String,
            @SerialName("updated_at") val updatedAt: String,
            @SerialName("is_anonymous") val isAnonymous: Boolean
        ) {
            @Serializable
            data class AppMetadata(
                val provider: String,
                val providers: List<String>
            )

            @Serializable
            data class UserMetadata(
                val email: String,
                @SerialName("email_verified") val emailVerified: Boolean,
                @SerialName("phone_verified") val phoneVerified: Boolean,
                val sub: String
            )

            @Serializable
            data class Identity(
                @SerialName("identity_id") val identityId: String,
                val id: String,
                @SerialName("user_id") val userId: String,
                @SerialName("identity_data") val identityData: IdentityData,
                val provider: String,
                @SerialName("last_sign_in_at") val lastSignInAt: String,
                @SerialName("created_at") val createdAt: String,
                @SerialName("updated_at") val updatedAt: String,
                val email: String
            ) {
                @Serializable
                data class IdentityData(
                    val email: String,
                    @SerialName("email_verified") val emailVerified: Boolean,
                    @SerialName("phone_verified") val phoneVerified: Boolean,
                    val sub: String
                )
            }
        }

        @Serializable
        data class Session(
            @SerialName("access_token") val accessToken: String,
            @SerialName("token_type") val tokenType: String,
            @SerialName("expires_in") val expiresIn: Long,
            @SerialName("expires_at") val expiresAt: Long,
            @SerialName("refresh_token") val refreshToken: String,
            val user: User
        )
    }

    init {
        needAuthenticate = false
    }
    override fun parseResponse(statusCode: Int, response: String): RefreshTokenResponse {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(response)
    }
}