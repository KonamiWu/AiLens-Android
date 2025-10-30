package com.konami.ailens.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.konami.ailens.R
import com.konami.ailens.agent.AgentConnectionWorker
import com.konami.ailens.agent.AgentService
import com.konami.ailens.agent.Environment
import com.konami.ailens.ble.command.ReadBatteryCommand
import com.konami.ailens.navigation.NavigationService
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.role.AgentRole
import com.konami.ailens.orchestrator.role.BluetoothRole
import com.konami.ailens.orchestrator.role.NavigationRole
import com.konami.ailens.recorder.BluetoothRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class AppForegroundService : Service() {
    companion object {
        const val NOTIFICATION_ID = 9001  // Use a unique ID far from Google's typical IDs
        const val ACTION_UPDATE_NOTIFICATION = "com.konami.ailens.action.UPDATE_NOTIFICATION"
        const val ACTION_START_BLE = "com.konami.ailens.action.START_BLE"
        const val ACTION_STOP_SCAN = "com.konami.ailens.action.STOP_SCAN"

        const val EXTRA_TEXT = "extra_text"
    }

    val bleService = BLEService.instance
    val navigationService = NavigationService.instance
    val agentService = AgentService.instance

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Track connection states
    private var isBluetoothConnected = false
    private var isAgentReady = false
    private var isForegroundStarted = false

    // Wake Lock to keep service alive in background
    private var wakeLock: PowerManager.WakeLock? = null

    // Doze Mode receiver
    private val dozeModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                    val isDozing = powerManager.isDeviceIdleMode
                    Log.w("AppForegroundService", "Doze mode changed: isDozing=$isDozing")

                    if (!isDozing && agentService.isConnected.value && !agentService.isReady.value) {
                        // Exited doze mode, reconnect agent if needed
                        Log.d("AppForegroundService", "Exited doze mode, checking agent connection")
                        serviceScope.launch {
                            if (!agentService.isConnected.value) {
                                reconnectAgent()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Acquire Wake Lock to keep service alive
        acquireWakeLock()

        // Register Doze Mode receiver
        registerDozeModeReceiver()

        // Schedule periodic connection check with WorkManager
        scheduleConnectionCheck()

        bind()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release Wake Lock
        releaseWakeLock()

        // Unregister Doze Mode receiver
        try {
            unregisterReceiver(dozeModeReceiver)
        } catch (e: Exception) {
            Log.e("AppForegroundService", "Failed to unregister receiver: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Only start foreground service once
            if (!isForegroundStarted) {
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
                isForegroundStarted = true
                Log.d("AppForegroundService", "Service started in foreground")
            } else {
                // Already in foreground, just update notification
                updateNotificationForCurrentState()
                Log.d("AppForegroundService", "Service already running, notification updated")
            }
        } catch (e: Exception) {
            Log.e("AppForegroundService", "Failed to start/update foreground service: ${e.message}", e)
        }

        val action = intent?.action
        if (action == ACTION_UPDATE_NOTIFICATION) {
            val text = intent.getStringExtra(EXTRA_TEXT)
            if (text != null) {
                updateNotification(text)
            } else {
                updateNotificationForCurrentState()
            }
        }

        return START_STICKY
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun bind() {
        val service = BLEService.instance
        val orchestrator = Orchestrator.instance
        service.retrieve()
        // Monitor Bluetooth connection state
        serviceScope.launch {
            service.connectedSession
                .flatMapLatest { session ->
                    if (session == null) {
                        orchestrator.clean()
                        isBluetoothConnected = false
                        updateNotificationForCurrentState()
                        emptyFlow()
                    } else {
                        session.state
                    }
                }
                .collect { state ->
                    when (state) {
                        DeviceSession.State.CONNECTED -> {
                            val session = service.connectedSession.value ?: return@collect
                            isBluetoothConnected = true
                            updateNotificationForCurrentState()

                            session.add(ReadBatteryCommand())
                            val orchestrator = Orchestrator.instance

                            AgentService.instance.connect(
                                getString(R.string.token),
                                Environment.Dev.config,
                                "agent",
                                "en"
                            )
                            val bluetoothRole = BluetoothRole(session, orchestrator)
                            val agentRole = AgentRole(orchestrator, BluetoothRecorder(session))
                            val navigationRole = NavigationRole()
                            orchestrator.register(bluetoothRole)
                            orchestrator.register(agentRole)
                            orchestrator.register(navigationRole)
                        }

                        DeviceSession.State.DISCONNECTED -> {
                            isBluetoothConnected = false
                            updateNotificationForCurrentState()
                            orchestrator.clean()
                            AgentService.instance.disconnect()
                        }

                        else -> {
                        }
                    }
                }
        }

        // Monitor Agent ready state
        serviceScope.launch {
            AgentService.instance.isReady.collect { isReady ->
                Log.e("AppForegroundService", "Agent ready state changed: $isReady")
                isAgentReady = isReady
                updateNotificationForCurrentState()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "ailens_app_service"
        val channel = NotificationChannel(
            channelId, "AiLens Service", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains connection to glasses device"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AiLens")
            .setContentText(getString(R.string.notification_agent_text))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, "ailens_app_service")
            .setContentTitle("AiLens")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotificationForCurrentState() {
        val statusParts = mutableListOf<String>()

        if (isBluetoothConnected) {
            statusParts.add(getString(R.string.notification_glasses_status))
        }

        if (isAgentReady) {
            statusParts.add(getString(R.string.notification_agent_status))
        }

        val statusText = if (statusParts.isEmpty()) {
            getString(R.string.notification_service_running)
        } else {
            statusParts.joinToString(getString(R.string.notification_separator))
        }

        updateNotification(statusText)
    }

    /**
     * Acquire Wake Lock to keep CPU running when screen is off
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AiLens::AppForegroundServiceWakeLock"
            ).apply {
                acquire()
                Log.d("AppForegroundService", "Wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e("AppForegroundService", "Failed to acquire wake lock: ${e.message}")
        }
    }

    /**
     * Release Wake Lock
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d("AppForegroundService", "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("AppForegroundService", "Failed to release wake lock: ${e.message}")
        }
    }

    /**
     * Register Doze Mode receiver to monitor device idle state
     */
    private fun registerDozeModeReceiver() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val filter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                registerReceiver(dozeModeReceiver, filter)
                Log.d("AppForegroundService", "Doze mode receiver registered")
            }
        } catch (e: Exception) {
            Log.e("AppForegroundService", "Failed to register doze mode receiver: ${e.message}")
        }
    }

    /**
     * Reconnect agent when exiting doze mode
     */
    private fun reconnectAgent() {
        try {
            val session = bleService.connectedSession.value
            if (session != null && session.state.value == DeviceSession.State.CONNECTED) {
                Log.d("AppForegroundService", "Reconnecting agent after doze mode")
                AgentService.instance.connect(
                    getString(R.string.token),
                    Environment.Dev.config,
                    "agent",
                    "en"
                )
            }
        } catch (e: Exception) {
            Log.e("AppForegroundService", "Failed to reconnect agent: ${e.message}")
        }
    }

    /**
     * Schedule periodic connection check using WorkManager
     * This ensures Agent connection is maintained even in background
     */
    private fun scheduleConnectionCheck() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<AgentConnectionWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES  // Check every 15 minutes (minimum allowed)
            )
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiresDeviceIdle(false)  // Allow running even in Doze mode
                        .setRequiresBatteryNotLow(false)  // Allow running even when battery is low
                        .build()
                )
                .build()

            WorkManager.getInstance(applicationContext)
                .enqueueUniquePeriodicWork(
                    AgentConnectionWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,  // Keep existing if already scheduled
                    workRequest
                )

            Log.e("AppForegroundService", "WorkManager connection check scheduled")
        } catch (e: Exception) {
            Log.e("AppForegroundService", "Failed to schedule WorkManager: ${e.message}")
        }
    }
}