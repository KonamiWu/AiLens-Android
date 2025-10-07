package com.konami.ailens

import android.app.Application
import android.util.Log
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator

class AiLensApplication : Application() {
    companion object {
        lateinit var instance: AiLensApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
