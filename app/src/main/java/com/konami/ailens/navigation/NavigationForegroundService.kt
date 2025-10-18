//package com.konami.ailens.navigation
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.os.IBinder
//import androidx.core.app.NotificationCompat
//
//class NavigationForegroundService : Service() {
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    override fun onCreate() {
//        super.onCreate()
//
//        NavigationService.init(applicationContext)
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        startForeground(NOTIFICATION_ID, createNotification())
//        return START_STICKY
//    }
//
//    private fun createNotification(): Notification {
//        val channelId = "navigation_channel"
//        val manager = getSystemService(NotificationManager::class.java)
//        val channel = NotificationChannel(
//            channelId, "Navigation Service", NotificationManager.IMPORTANCE_DEFAULT
//        ).apply {
//            description = "Keeps navigation running in background"
//            setShowBadge(false)
//        }
//        manager.createNotificationChannel(channel)
//
//        return NotificationCompat.Builder(this, channelId)
//            .setContentTitle("Navigation Active")
//            .setContentText("Navigating to destination")
//            .setSmallIcon(android.R.drawable.ic_dialog_map)
//            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setOngoing(true)
//            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
//            .setGroup("ailens_services")
//            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
//            .build()
//    }
//
//    companion object {
//        const val NOTIFICATION_ID = 1003
//
//        fun start(context: Context) {
//            val intent = Intent(context, NavigationForegroundService::class.java)
//            context.startForegroundService(intent)
//        }
//
//        fun stop(context: Context) {
//            val intent = Intent(context, NavigationForegroundService::class.java)
//            context.stopService(intent)
//        }
//    }
//}
//
