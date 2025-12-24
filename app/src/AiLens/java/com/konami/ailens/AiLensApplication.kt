package com.konami.ailens

import android.app.Application
import com.konami.ailens.ble.BLEService
import com.konami.ailens.navigation.NavigationService
import com.konami.ailens.orchestrator.Orchestrator

class AiLensApplication : Application() {
    companion object {
        lateinit var instance: AiLensApplication
            private set
    }

    val navigationEnable = true

    override fun onCreate() {
        super.onCreate()
        instance = this
        SharedPrefs.init(this)
        Orchestrator.init(this)
        BLEService.init(this)
        if (navigationEnable)
            NavigationService.init(this)
    }
}
