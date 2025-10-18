package com.konami.ailens.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AgentForegroundService: Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "agent_channel"
        val channel = NotificationChannel(
            channelId, "AI Agent", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "AI assistant service for glasses"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AI Agent Active")
            .setContentText("Ready to assist")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setGroup("ailens_services")
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1002
    }
}