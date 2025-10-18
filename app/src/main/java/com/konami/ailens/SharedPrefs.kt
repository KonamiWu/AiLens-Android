package com.konami.ailens

import android.content.Context
import android.content.SharedPreferences
import android.media.session.MediaSession
import android.util.Base64
import com.google.gson.Gson
import androidx.core.content.edit

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
    private const val MAX_HISTORY_SIZE = 5
    private val tokenManager = TokenManager()
    private lateinit var prefs: SharedPreferences

    lateinit var instance: SharedPrefs

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        instance = this
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
                prefs.edit().remove(KEY_HOME_FAVORITE).apply()
            } else {
                val gson = Gson()
                val jsonStr = gson.toJson(value)
                prefs.edit().putString(KEY_HOME_FAVORITE, jsonStr).apply()
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
            val type = object : com.google.gson.reflect.TypeToken<List<AddressHistoryItem>>() {}.type
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
        prefs.edit().putString(KEY_ADDRESS_HISTORY, jsonStr).apply()
    }

    fun filterAddressHistory(query: String): List<AddressHistoryItem> {
        if (query.isBlank()) return emptyList()

        val lowerQuery = query.lowercase()
        return getAddressHistory().filter {
            it.mainText.lowercase().contains(lowerQuery) ||
            it.secondaryText.lowercase().contains(lowerQuery)
        }
    }

    fun saveDeviceInfo(context: Context, mac: String, userId: UInt, deviceToken: ByteArray) {

        val prefs = getPrefs(context)
        val gson = Gson()
        val jsonMap = mapOf(
            "mac" to mac,
            "retrieveToken" to Base64.encodeToString(tokenManager.getRetrieveToken(mac, userId, deviceToken), Base64.NO_WRAP)
        )
        val jsonStr = gson.toJson(jsonMap)
        prefs.edit().putString(KEY_DEVICE_INFO, jsonStr).apply()
    }

    fun getDeviceInfo(context: Context): DeviceInfo? {
        val prefs = getPrefs(context)
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

    fun clearDeviceInfo(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit { remove(KEY_DEVICE_INFO) }
    }
}
