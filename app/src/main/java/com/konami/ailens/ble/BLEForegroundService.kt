package com.konami.ailens.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.konami.ailens.PermissionHelper

class BLEForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        // 建立單例，不做任何需要權限的動作
        BLEService.init(applicationContext)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        val hasPerm = PermissionHelper.hasAllPermissions(this)
        when (intent?.action) {
            ACTION_START_BLE -> if (hasPerm) {
                BLEService.instance.retrieve()
                BLEService.instance.startScan()
            }
            ACTION_STOP_SCAN -> if (hasPerm) {
                BLEService.instance.stopScan()
            }
            else -> { }
        }
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
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_BLE = "com.konami.ailens.action.START_BLE"
        const val ACTION_STOP_SCAN = "com.konami.ailens.action.STOP_SCAN"
    }
}