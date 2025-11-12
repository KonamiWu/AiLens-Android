package com.konami.ailens

import android.app.Application
import android.util.Log
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.konami.ailens.ble.BLEService
import com.konami.ailens.navigation.NavigationService
import com.konami.ailens.orchestrator.Orchestrator

class AiLensApplication : Application() {
    companion object {
        lateinit var instance: AiLensApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        SharedPrefs.init(this)
        Orchestrator.init(this)
        BLEService.init(this)
        NavigationService.init(this)
    }
}
