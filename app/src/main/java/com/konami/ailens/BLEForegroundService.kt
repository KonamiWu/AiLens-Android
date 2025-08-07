package com.konami.ailens

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.Service.START_STICKY
import android.content.Intent
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat

class BLEForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        BLEService.init(applicationContext)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        BLEService.instance.retrieve()
        BLEService.instance.scan()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "ble_channel"
        val channel = NotificationChannel(
            channelId, "BLE Service", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bluetooth")
            .setContentText("BLE Service is running")
            .build()
    }

    companion object {
        const val TAG = "BLEForegroundService"
        const val NOTIFICATION_ID = 1001
    }
}