//package com.konami.ailens
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.content.pm.ServiceInfo
//import android.os.Build
//import android.os.IBinder
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import androidx.core.app.ServiceCompat
//
//class AppForegroundService_TEMP : Service() {
//
//    enum class Feature {
//        BLE,
//        NAVIGATION,
//        AGENT
//    }
//
//    companion object {
//        private const val TAG = "AppForegroundService"
//        private const val NOTIFICATION_ID = 1000
//        private const val CHANNEL_ID = "ailens_service_channel"
//
//        private val activeFeatures = mutableSetOf<Feature>()
//        private var bleConnected = false  // Track BLE connection state
//        private var agentReady = false  // Track Agent ready state (Gemini session opened)
//
//        private const val EXTRA_ACTION = "action"
//        private const val EXTRA_FEATURE = "feature"
//        private const val EXTRA_BLE_CONNECTED = "ble_connected"
//        private const val EXTRA_AGENT_READY = "agent_ready"
//        private const val ACTION_START_FEATURE = "start_feature"
//        private const val ACTION_STOP_FEATURE = "stop_feature"
//        private const val ACTION_UPDATE_BLE_STATE = "update_ble_state"
//        private const val ACTION_UPDATE_AGENT_STATE = "update_agent_state"
//
//        private var serviceInstance: AppForegroundService_TEMP? = null
//
//        /**
//         * 啟動特定功能
//         */
//        fun startFeature(context: Context, feature: Feature) {
//            Log.d(TAG, "Starting feature: $feature")
//            val intent = Intent(context, AppForegroundService_TEMP::class.java).apply {
//                putExtra(EXTRA_ACTION, ACTION_START_FEATURE)
//                putExtra(EXTRA_FEATURE, feature.name)
//            }
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                context.startForegroundService(intent)
//            } else {
//                context.startService(intent)
//            }
//        }
//
//        /**
//         * 停止特定功能
//         */
//        fun stopFeature(context: Context, feature: Feature) {
//            Log.d(TAG, "Stopping feature: $feature")
//            val intent = Intent(context, AppForegroundService_TEMP::class.java).apply {
//                putExtra(EXTRA_ACTION, ACTION_STOP_FEATURE)
//                putExtra(EXTRA_FEATURE, feature.name)
//            }
//            context.startService(intent)
//        }
//
//        /**
//         * 更新 BLE 連線狀態
//         * 使用 startService 觸發 onStartCommand，從而調用 updateNotification() 更新通知
//         */
//        fun updateBleConnectionState(context: Context, isConnected: Boolean) {
//            Log.d(TAG, "Requesting BLE connection state update: $isConnected")
//            val intent = Intent(context, AppForegroundService_TEMP::class.java).apply {
//                putExtra(EXTRA_ACTION, ACTION_UPDATE_BLE_STATE)
//                putExtra(EXTRA_BLE_CONNECTED, isConnected)
//            }
//            context.startService(intent)
//        }
//
//        /**
//         * 更新 Agent 就緒狀態 (Gemini session opened)
//         * 使用 startService 觸發 onStartCommand，從而調用 updateNotification() 更新通知
//         */
//        fun updateAgentReadyState(context: Context, isReady: Boolean) {
//            Log.d(TAG, "Requesting Agent ready state update: $isReady")
//            val intent = Intent(context, AppForegroundService_TEMP::class.java).apply {
//                putExtra(EXTRA_ACTION, ACTION_UPDATE_AGENT_STATE)
//                putExtra(EXTRA_AGENT_READY, isReady)
//            }
//            context.startService(intent)
//        }
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        serviceInstance = this
//        Log.d(TAG, "Service created")
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        val action = intent?.getStringExtra(EXTRA_ACTION)
//        val featureName = intent?.getStringExtra(EXTRA_FEATURE)
//
//        when (action) {
//            ACTION_START_FEATURE -> {
//                featureName?.let {
//                    try {
//                        val feature = Feature.valueOf(it)
//                        synchronized(activeFeatures) {
//                            activeFeatures.add(feature)
//                        }
//                        Log.d(TAG, "Feature added: $feature, active features: $activeFeatures")
//                    } catch (e: IllegalArgumentException) {
//                        Log.e(TAG, "Invalid feature: $featureName", e)
//                    }
//                }
//            }
//            ACTION_STOP_FEATURE -> {
//                featureName?.let {
//                    try {
//                        val feature = Feature.valueOf(it)
//                        synchronized(activeFeatures) {
//                            activeFeatures.remove(feature)
//                        }
//                        Log.d(TAG, "Feature removed: $feature, active features: $activeFeatures")
//                    } catch (e: IllegalArgumentException) {
//                        Log.e(TAG, "Invalid feature: $featureName", e)
//                    }
//                }
//            }
//            ACTION_UPDATE_BLE_STATE -> {
//                val isConnected = intent?.getBooleanExtra(EXTRA_BLE_CONNECTED, false) ?: false
//                bleConnected = isConnected
//                Log.d(TAG, "BLE connection state updated: $bleConnected")
//                // Update notification immediately when BLE state changes
//                if (activeFeatures.isNotEmpty()) {
//                    updateNotification()
//                }
//                return START_STICKY  // Don't need to recreate notification below
//            }
//            ACTION_UPDATE_AGENT_STATE -> {
//                val isReady = intent?.getBooleanExtra(EXTRA_AGENT_READY, false) ?: false
//                agentReady = isReady
//                Log.d(TAG, "Agent ready state updated: $agentReady")
//                // Update notification immediately when Agent state changes
//                if (activeFeatures.isNotEmpty()) {
//                    updateNotification()
//                }
//                return START_STICKY  // Don't need to recreate notification below
//            }
//        }
//
//        // 如果沒有任何活躍功能，停止服務
//        if (activeFeatures.isEmpty()) {
//            Log.d(TAG, "No active features, stopping service")
//            stopSelf()
//            return START_NOT_STICKY
//        }
//
//        // 啟動前景服務並更新通知
//        try {
//            val serviceType = getServiceType()
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                ServiceCompat.startForeground(
//                    this,
//                    NOTIFICATION_ID,
//                    createNotification(),
//                    serviceType
//                )
//            } else {
//                startForeground(NOTIFICATION_ID, createNotification())
//            }
//            Log.d(TAG, "Service started in foreground with type: $serviceType")
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
//        }
//
//        return START_STICKY
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    override fun onDestroy() {
//        super.onDestroy()
//        synchronized(activeFeatures) {
//            activeFeatures.clear()
//        }
//        serviceInstance = null
//        Log.d(TAG, "Service destroyed")
//    }
//
//    /**
//     * 根據活躍功能組合 service type
//     */
//    private fun getServiceType(): Int {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
//            return 0
//        }
//
//        var type = 0
//        synchronized(activeFeatures) {
//            if (activeFeatures.contains(Feature.BLE)) {
//                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
//            }
//            if (activeFeatures.contains(Feature.NAVIGATION)) {
//                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
//            }
//            if (activeFeatures.contains(Feature.AGENT)) {
//                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
//                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
//            }
//        }
//        return type
//    }
//
//    /**
//     * 創建動態通知
//     */
//    private fun createNotification(): Notification {
//        val manager = getSystemService(NotificationManager::class.java)
//        val channel = NotificationChannel(
//            CHANNEL_ID,
//            "AiLens Service",
//            NotificationManager.IMPORTANCE_DEFAULT
//        ).apply {
//            description = "AiLens background services"
//            setShowBadge(false)
//        }
//        manager.createNotificationChannel(channel)
//
//        val (title, text) = getNotificationContent()
//
//        return NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle(title)
//            .setContentText(text)
//            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
//            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setOngoing(true)
//            .setCategory(NotificationCompat.CATEGORY_SERVICE)
//            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
//            .build()
//    }
//
//    /**
//     * 根據活躍功能生成通知內容
//     */
//    private fun getNotificationContent(): Pair<String, String> {
//        val features = synchronized(activeFeatures) { activeFeatures.toList() }
//
//        return when {
//            features.isEmpty() -> getString(R.string.app_name) to getString(R.string.notification_service_running)
//            features.size == 1 -> when (features[0]) {
//                Feature.BLE -> if (bleConnected) {
//                    getString(R.string.notification_glasses_connected_title) to
//                    getString(R.string.notification_glasses_connected_text)
//                } else {
//                    getString(R.string.notification_waiting_connection_title) to
//                    getString(R.string.notification_waiting_connection_text)
//                }
//                Feature.NAVIGATION ->
//                    getString(R.string.notification_navigation_title) to
//                    getString(R.string.notification_navigation_text)
//                Feature.AGENT -> if (agentReady) {
//                    getString(R.string.notification_agent_title) to
//                    getString(R.string.notification_agent_text)
//                } else {
//                    getString(R.string.notification_agent_connecting_title) to
//                    getString(R.string.notification_agent_connecting_text)
//                }
//            }
//            features.size == 2 -> {
//                val bleText = if (features.contains(Feature.BLE)) {
//                    if (bleConnected)
//                        getString(R.string.notification_glasses_status)
//                    else
//                        getString(R.string.notification_waiting_status)
//                } else null
//
//                val statusTexts = features.mapNotNull {
//                    when (it) {
//                        Feature.BLE -> bleText
//                        Feature.NAVIGATION -> getString(R.string.notification_navigation_status)
//                        Feature.AGENT -> if (agentReady) {
//                            getString(R.string.notification_agent_status)
//                        } else {
//                            getString(R.string.notification_agent_connecting_status)
//                        }
//                    }
//                }
//                getString(R.string.notification_ailens_running) to statusTexts.joinToString(getString(R.string.notification_separator))
//            }
//            else -> {
//                val separator = getString(R.string.notification_separator)
//                val bleStatus = if (bleConnected)
//                    getString(R.string.notification_glasses_status)
//                else
//                    getString(R.string.notification_waiting_status)
//                val agentStatus = if (agentReady)
//                    getString(R.string.notification_agent_status)
//                else
//                    getString(R.string.notification_agent_connecting_status)
//                val allStatus = listOf(
//                    bleStatus,
//                    getString(R.string.notification_navigation_status),
//                    agentStatus
//                )
//                getString(R.string.notification_ailens_running) to allStatus.joinToString(separator)
//            }
//        }
//    }
//
//    /**
//     * 更新通知（不改變 service type）
//     */
//    private fun updateNotification() {
//        if (activeFeatures.isEmpty()) return
//
//        val notification = createNotification()
//        val notificationManager = getSystemService(NotificationManager::class.java)
//        notificationManager.notify(NOTIFICATION_ID, notification)
//        Log.d(TAG, "Notification updated")
//    }
//}
