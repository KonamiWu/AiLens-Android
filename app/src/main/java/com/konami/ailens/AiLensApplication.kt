package com.konami.ailens

import android.app.Application
import android.util.Log
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.konami.ailens.ble.BLEService

class AiLensApplication : Application() {
    companion object {
        lateinit var instance: AiLensApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        SharedPrefs.init(this)

        // Initialize BLEService early to avoid race conditions
        // This ensures BLEService.instance is always available throughout the app lifecycle
        BLEService.init(this)
        Log.d("AiLensApplication", "BLEService initialized")
    }
}
