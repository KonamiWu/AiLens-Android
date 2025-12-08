package com.konami.ailens.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import com.konami.ailens.AiLensApplication

object SessionManager {

    private const val PREF_NAME   = "secure_session"
    private const val KEY_EMAIL  = "email"
    private const val KEY_ACCESS  = "accessToken"
    private const val KEY_REFRESH = "refreshToken"
    private const val KEY_EXPIRES_AT = "expiresAt"
    private const val KEY_USER_ID  = "userId"

    private val context: Context
        get() = AiLensApplication.instance.applicationContext

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isLoggedIn(): Boolean = prefs.contains(KEY_ACCESS)
    fun isTokenExpired(): Boolean {
        val expiresAt = getExpiresAt()
        val now = System.currentTimeMillis() / 1000 // 轉換為秒
        return expiresAt <= now
    }

    fun saveTokens(access: String, refresh: String, expiresAt: Long) = prefs.edit() {
        putString(KEY_ACCESS, access)
        putString(KEY_REFRESH, refresh)
        putLong(KEY_EXPIRES_AT, expiresAt)
    }

    fun saveEmail(email: String) = prefs.edit() {
        putString(KEY_EMAIL, email)
    }

    fun saveUserId(userId: String) = prefs.edit {
        putString(KEY_USER_ID, userId)
    }

    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun logout() = prefs.edit() { clear() }
}
