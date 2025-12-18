package com.konami.ailens.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.konami.ailens.R
import com.konami.ailens.ble.command.ota.GetVersionListCommand
import com.konami.ailens.ble.command.ota.OTAFailedCommand
import com.konami.ailens.ble.command.ota.OTAStartCommand
import com.konami.ailens.ble.command.ota.RebootCommand
import com.konami.ailens.ble.command.ota.SendOTADataCommand
import com.konami.ailens.ble.command.ota.SetOTAModeCommand
import com.konami.ailens.ble.command.ota.SetVersionCommand
import com.konami.ailens.ble.ota.CommonDataFramer
import com.konami.ailens.ble.ota.FirmwareFile
import com.konami.ailens.ble.ota.OTAHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Foreground Service for firmware OTA update
 * Handles the entire V1 OTA process
 * Reference iOS: XRGlassesOTAService
 */
class FirmwareUpdateService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val binder = LocalBinder()

    sealed class UpdateState {
        object Idle : UpdateState()
        object Updating : UpdateState()
        object Completed : UpdateState()
        data class Failed(val error: String) : UpdateState()
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var glasses: Glasses? = null
    private var updateJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): FirmwareUpdateService = this@FirmwareUpdateService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun startForegroundService() {
        val channelId = "firmware_update_channel"
        val channelName = "Firmware Update"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification = createNotification(channelId, "Preparing firmware update...", 0)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(channelId: String, contentText: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.check_update_firmware_update))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_nav_back)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String, progress: Float) {
//        val notification = createNotification("firmware_update_channel", contentText, progress)
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Start V1 OTA update process
     * Reference iOS: startUpdate method
     */
    fun startUpdate(glasses: Glasses, firmwarePath: String) {
        this.glasses = glasses
        _state.value = UpdateState.Updating
        _progress.value = 0f

        updateJob = scope.launch {
            try {
                performOTAUpdate(glasses, firmwarePath)
            } catch (e: CancellationException) {
                Log.e(TAG, "OTA update cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OTA update failed: ${e.message}", e)
                _state.value = UpdateState.Failed(e.message ?: "Unknown error")
                updateNotification("Update failed: ${e.message}", 0f)
            }
        }
    }

    private suspend fun performOTAUpdate(glasses: Glasses, firmwarePath: String) {
        // Step 1: Parse firmware file
        updateNotification("Reading firmware file...", 0f)

        val firmwareFile = FirmwareFile.parse(File(firmwarePath)).getOrThrow()
        Log.e(TAG, "Parsed firmware file: ${firmwareFile.sections.size} sections")

        // Step 2: Get device version list
        updateNotification("Reading device versions...", 0f)

        val deviceVersions = executeCommand(glasses, GetVersionListCommand())
        Log.e(TAG, "Device versions: $deviceVersions")

        // Step 3: Filter sections that need update
        val updateSections = FirmwareFile.filterUpdateSections(
            firmwareFile,
            deviceVersions,
            forceUpdate = true
        )

        if (updateSections.isEmpty()) {
            _state.value = UpdateState.Completed
            updateNotification("Already latest version", 100f)
            delay(2000)
            stopSelf()
            return
        }

        Log.e(TAG, "Sections to update: ${updateSections.size}")

        val totalUpdateLength = updateSections.sumOf {
            (it.header.fwLength + OTAHeader.SIZE.toUInt()).toLong()
        }.toUInt()

        Log.e(TAG, "Total update length: $totalUpdateLength bytes")

        kotlin.coroutines.coroutineContext.ensureActive()

        // Step 4: Send OTA start command
        updateNotification("Starting OTA...", 0f)

        executeCommand(glasses, OTAStartCommand(totalUpdateLength))
        delay(500)

        kotlin.coroutines.coroutineContext.ensureActive()

        // Step 5: Transfer firmware data
        var totalSentPackets = 0
        var totalPacketsCount = 0

        for (section in updateSections) {
            val sectionData = section.fullData
            val binType = (section.header.fwDataType and 0xFFu).toUByte()
            val framer = CommonDataFramer(binType = binType, data = sectionData, mtu = glasses.mtu)
            totalPacketsCount += framer.getPacketCount()
        }

        for ((sectionIndex, section) in updateSections.withIndex()) {
            kotlin.coroutines.coroutineContext.ensureActive()

            Log.e(TAG, "Sending section ${sectionIndex + 1}/${updateSections.size}: type=${section.header.fwDataType}")

            // Send OTA header (48 bytes) + firmware data (matching iOS behavior)
            val sectionData = section.fullData  // OTA header + firmware data

            // Use low byte of fwDataType directly (matches iOS XRCommonData bin_type)
            val binType = (section.header.fwDataType and 0xFFu).toUByte()
            Log.e(TAG, "Using binType=0x${binType.toString(16)} (from fwDataType=0x${section.header.fwDataType.toString(16)})")

            val framer = CommonDataFramer(
                binType = binType,
                data = sectionData,
                mtu = glasses.mtu
            )

            val totalPackets = framer.getPacketCount()
            Log.e(TAG, "Section has $totalPackets packets to send")

            for (packetIndex in 0 until totalPackets) {
                kotlin.coroutines.coroutineContext.ensureActive()

                val packet = framer.getPacket(packetIndex) ?: continue

                // Send packet and wait for device acknowledgment
                val response = executeCommand(glasses, SendOTADataCommand(packet, packetIndex, totalPackets))

                // Response will throw exception if status != 0x00
                Log.e(TAG, "Device confirmed packet $packetIndex (index=${response.index})")

                totalSentPackets++

                val overallProgress = totalSentPackets.toFloat() / totalPacketsCount.toFloat()
                _progress.value = overallProgress
                updateNotification("Transferring firmware... ${(overallProgress * 100).toInt()}%", overallProgress)
            }

            Log.e(TAG, "Section ${sectionIndex + 1} completed")
        }

        kotlin.coroutines.coroutineContext.ensureActive()

        // Step 6: Set OTA mode
        updateNotification("Setting OTA mode...", 100f)

        executeCommand(glasses, SetOTAModeCommand())

        kotlin.coroutines.coroutineContext.ensureActive()

        // Step 7: Update version information (use BAG header version, not individual sections)
        updateNotification("Updating version info...", 100f)

        executeCommand(
            glasses,
            SetVersionCommand(firmwareFile.bagHeader.version)
        )

        kotlin.coroutines.coroutineContext.ensureActive()

        // Step 8: Reboot device
        updateNotification("Rebooting device...", 100f)

        executeCommandSafe(glasses, RebootCommand())

        // Step 9: Complete
        _state.value = UpdateState.Completed
        _progress.value = 1f
        updateNotification("Update completed successfully!", 100f)

        Log.e(TAG, "OTA update completed successfully")

        stopSelf()
    }

    /**
     * Execute command and return Result (does not throw exception)
     */
    private suspend fun <T> executeCommandSafe(glasses: Glasses, command: com.konami.ailens.ble.command.BLECommand<T>): Result<T> {
        return suspendCoroutine { continuation ->
            command.completion = { result ->
                // Resume in IO dispatcher to avoid thread issues
                scope.launch(Dispatchers.IO) {
                    continuation.resume(result)
                }
            }
            glasses.add(command)
        }
    }

    /**
     * Execute command and unwrap result (throws exception on failure)
     */
    private suspend fun <T> executeCommand(glasses: Glasses, command: com.konami.ailens.ble.command.BLECommand<T>): T {
        return suspendCoroutine { continuation ->
            command.completion = { result ->
                // Resume in IO dispatcher to avoid thread issues
                scope.launch(Dispatchers.IO) {
                    result.onSuccess { value ->
                        continuation.resume(value)
                    }.onFailure { error ->
                        continuation.resumeWithException(error)
                    }
                }
            }
            glasses.add(command)
        }
    }

    /**
     * Cancel ongoing update
     */
    fun cancelUpdate() {
        val currentGlasses = glasses ?: return

        updateJob?.cancel()
        updateJob = null

        _state.value = UpdateState.Idle
        _progress.value = 0f

        scope.launch {
            try {
                executeCommandSafe(currentGlasses, OTAFailedCommand())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send OTA cancel command: ${e.message}")
            }
        }

        stopSelf()
    }

    companion object {
        private const val TAG = "FirmwareUpdateService"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context): Intent {
            val intent = Intent(context, FirmwareUpdateService::class.java)
            context.startForegroundService(intent)
            return intent
        }
    }
}
