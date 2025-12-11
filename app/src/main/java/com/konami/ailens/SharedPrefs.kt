package com.konami.ailens

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.gson.Gson
import androidx.core.content.edit
import com.google.gson.reflect.TypeToken
import com.konami.ailens.orchestrator.Orchestrator

data class DeviceInfo(
    val mac: String,
    val retrieveToken: ByteArray
)

data class AddressHistoryItem(
    val lat: Double,
    val lng: Double,
    val mainText: String,
    val secondaryText: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class FavoriteLocation(
    val lat: Double,
    val lng: Double,
    val mainText: String,
    val secondaryText: String
)

object SharedPrefs {
    private const val PREFS_NAME = "YourAppPrefs"
    private const val KEY_DEVICE_INFO = "deviceInfo"
    private const val KEY_HOME_FAVORITE = "homeFavorite"
    private const val KEY_COMPANY_FAVORITE = "companyFavorite"
    private const val KEY_ADDRESS_HISTORY = "addressHistory"
    private const val KEY_INTERPRETATION_SOURCE_LANGUAGE = "interpretationSource"
    private const val KEY_INTERPRETATION_TARGET_LANGUAGE = "interpretationTarget"
    private const val DIALOG_SOURCE_LANGUAGE = "twoWaySource"
    private const val DIALOG_TARGET_LANGUAGE = "twoWayTarget"
    private const val DIALOG_BILINGUAL = "bilingual"
    private const val KEY_NAVIGATION_TERMS_ACCEPTED = "navigationTermsAccepted"
    private const val MAX_HISTORY_SIZE = 5
    private val tokenManager = TokenManager()
    private lateinit var prefs: SharedPreferences

    lateinit var instance: SharedPrefs

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        instance = this
    }

    var navigationTermsAccepted: Boolean
        get() = prefs.getBoolean(KEY_NAVIGATION_TERMS_ACCEPTED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_NAVIGATION_TERMS_ACCEPTED, value) }
        }

    var homeFavorite: FavoriteLocation?
        get() {
            val gson = Gson()
            val jsonStr = prefs.getString(KEY_HOME_FAVORITE, null) ?: return null
            return try {
                gson.fromJson(jsonStr, FavoriteLocation::class.java)
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit { remove(KEY_HOME_FAVORITE) }
            } else {
                val gson = Gson()
                val jsonStr = gson.toJson(value)
                prefs.edit { putString(KEY_HOME_FAVORITE, jsonStr) }
            }
        }

    var bilingual: Boolean
        get() {
            return prefs.getBoolean(DIALOG_BILINGUAL, false)
        }
        set(value) {
            prefs.edit {
                putBoolean(DIALOG_BILINGUAL, value)
            }
        }

    var companyFavorite: FavoriteLocation?
        get() {
            val gson = Gson()
            val jsonStr = prefs.getString(KEY_COMPANY_FAVORITE, null) ?: return null
            return try {
                gson.fromJson(jsonStr, FavoriteLocation::class.java)
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_COMPANY_FAVORITE).apply()
            } else {
                val gson = Gson()
                val jsonStr = gson.toJson(value)
                prefs.edit().putString(KEY_COMPANY_FAVORITE, jsonStr).apply()
            }
        }

    // Address History Management
    fun getAddressHistory(): List<AddressHistoryItem> {
        val gson = Gson()
        val jsonStr = prefs.getString(KEY_ADDRESS_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AddressHistoryItem>>() {}.type
            gson.fromJson(jsonStr, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addAddressToHistory(item: AddressHistoryItem) {
        val currentHistory = getAddressHistory().toMutableList()

        // Remove duplicate if exists (by coordinates)
        currentHistory.removeAll { it.lat == item.lat && it.lng == item.lng }

        // Add new item at the beginning
        currentHistory.add(0, item)

        // Keep only the latest MAX_HISTORY_SIZE items
        val updatedHistory = currentHistory.take(MAX_HISTORY_SIZE)

        // Save to SharedPreferences
        val gson = Gson()
        val jsonStr = gson.toJson(updatedHistory)
        prefs.edit { putString(KEY_ADDRESS_HISTORY, jsonStr) }
    }

    fun filterAddressHistory(query: String): List<AddressHistoryItem> {
        if (query.isBlank()) return emptyList()

        val lowerQuery = query.lowercase()
        return getAddressHistory().filter {
            it.mainText.lowercase().contains(lowerQuery) ||
            it.secondaryText.lowercase().contains(lowerQuery)
        }
    }

    fun saveDeviceInfo(mac: String, userId: UInt, deviceToken: ByteArray) {
        val gson = Gson()
        val jsonMap = mapOf(
            "mac" to mac,
            "retrieveToken" to Base64.encodeToString(tokenManager.getRetrieveToken(mac, userId, deviceToken), Base64.NO_WRAP)
        )
        val jsonStr = gson.toJson(jsonMap)
        prefs.edit { putString(KEY_DEVICE_INFO, jsonStr) }
    }

    var interpretationSourceLanguage: Orchestrator.Language
        get() {
            val code = prefs.getString(
                KEY_INTERPRETATION_SOURCE_LANGUAGE,
                Orchestrator.Language.interpretationSourceDefault.code
            )
            return Orchestrator.Language.fromCode(code ?: "")
                ?: Orchestrator.Language.interpretationSourceDefault
        }
        set(value) {
            prefs.edit {
                putString(KEY_INTERPRETATION_SOURCE_LANGUAGE, value.code)
            }
        }

    var interpretationTargetLanguage: Orchestrator.Language
        get() {
            val code = prefs.getString(
                KEY_INTERPRETATION_TARGET_LANGUAGE,
                Orchestrator.Language.interpretationTargetDefault.code
            )
            return Orchestrator.Language.fromCode(code ?: "")
                ?: Orchestrator.Language.interpretationTargetDefault
        }
        set(value) {
            prefs.edit {
                putString(KEY_INTERPRETATION_TARGET_LANGUAGE, value.code)
            }
        }

    var dialogSourceLanguage: Orchestrator.Language
        get() {
            val code = prefs.getString(
                DIALOG_SOURCE_LANGUAGE,
                Orchestrator.Language.interpretationSourceDefault.code
            )
            return Orchestrator.Language.fromCode(code ?: "")
                ?: Orchestrator.Language.interpretationSourceDefault
        }
        set(value) {
            prefs.edit {
                putString(DIALOG_SOURCE_LANGUAGE, value.code)
            }
        }

    var dialogTargetLanguage: Orchestrator.Language
        get() {
            val code = prefs.getString(
                DIALOG_TARGET_LANGUAGE,
                Orchestrator.Language.interpretationTargetDefault.code
            )
            return Orchestrator.Language.fromCode(code ?: "")
                ?: Orchestrator.Language.interpretationTargetDefault
        }
        set(value) {
            prefs.edit {
                putString(DIALOG_TARGET_LANGUAGE, value.code)
            }
        }

    fun getDeviceInfo(): DeviceInfo? {
        val gson = Gson()
        val jsonStr = prefs.getString(KEY_DEVICE_INFO, null) ?: return null

        return try {
            val map = gson.fromJson(jsonStr, Map::class.java)
            val mac = map["mac"] as? String ?: return null
            val tokenBase64 = map["retrieveToken"] as? String ?: return null
            val tokenBytes = Base64.decode(tokenBase64, Base64.DEFAULT)
            DeviceInfo(mac, tokenBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearDeviceInfo() {
        prefs.edit { remove(KEY_DEVICE_INFO) }
    }

    fun cleanUp() {
        prefs.edit {
            clear()
        }
    }
}
