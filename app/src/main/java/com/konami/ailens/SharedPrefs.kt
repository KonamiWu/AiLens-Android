package com.konami.ailens

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.gson.Gson

data class DeviceInfo(
    val mac: String,
    val retrieveToken: ByteArray
)

object SharedPrefs {
    private const val PREFS_NAME = "YourAppPrefs"
    private const val KEY_DEVICE_INFO = "deviceInfo"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveDeviceInfo(context: Context, deviceInfo: DeviceInfo) {
        val prefs = getPrefs(context)
        val gson = Gson()
        val jsonMap = mapOf(
            "mac" to deviceInfo.mac,
            "retrieveToken" to Base64.encodeToString(deviceInfo.retrieveToken, Base64.NO_WRAP)
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
        prefs.edit().remove(KEY_DEVICE_INFO).apply()
    }
}
