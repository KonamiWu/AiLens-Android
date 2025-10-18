package com.konami.ailens.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.konami.ailens.PermissionHelper

class BLEForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        // BLEService is already initialized in AiLensApplication.onCreate()
        // No need to initialize here
        Log.d("BLEForegroundService", "Service created")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            Log.d("BLEForegroundService", "Service started in foreground")
        } catch (e: Exception) {
            Log.e("BLEForegroundService", "Failed to start foreground service: ${e.message}", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "ble_channel"
        val channel = NotificationChannel(
            channelId, "Bluetooth Connection", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Maintains connection to glasses device"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Glasses Connected")
            .setContentText("BLE connection active")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setGroup("ailens_services")
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_BLE = "com.konami.ailens.action.START_BLE"
        const val ACTION_STOP_SCAN = "com.konami.ailens.action.STOP_SCAN"
    }
}