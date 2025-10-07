package com.konami.ailens.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class NavigationForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Ensure map is initialized so navigation can run headless
        NavigationService.ensureMap(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "navigation_channel"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId, "Navigation Service", NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Navigation Service")
            .setContentText("Navigation is ready")
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1003

        fun start(context: Context) {
            val intent = Intent(context, NavigationForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }
}

