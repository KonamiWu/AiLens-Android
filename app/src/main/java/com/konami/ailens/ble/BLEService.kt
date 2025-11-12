package com.konami.ailens.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.konami.ailens.SharedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


@SuppressLint("MissingPermission")
class BLEService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BLEService"

        @SuppressLint("StaticFieldLeak")
        @Volatile private var _instance: BLEService? = null

        val instance: BLEService
            get() = _instance ?: throw IllegalStateException("Call BLEService.init(context) first")

        /**
         * Initialize BLEService singleton.
         * Must be called in Application.onCreate() before using BLEService.instance.
         *
         * Safe to call early - does not require Bluetooth permissions.
         * Only creates the instance and sets up internal structures.
         * Actual Bluetooth operations (scan, connect) are called later after permissions are granted.
         */
        fun init(context: Context) {
            if (_instance == null) {
                synchronized(this) {
                    if (_instance == null) {
                        _instance = BLEService(context.applicationContext)
                        Log.d(TAG, "BLEService initialized (no permissions required at this stage)")
                    }
                }
            }
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _updateFlow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val updateFlow = _updateFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    val sessions = mutableMapOf<String, Glasses>()
    private val sessionJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val _connectedSession = MutableStateFlow<Glasses?>(null)
    val connectedSession = _connectedSession.asStateFlow()

    private var reconnectJob: kotlinx.coroutines.Job? = null  // Track reconnect job

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val newDevice = result.device ?: return
            val address = newDevice.address ?: return
            if (_connectedSession.value?.device?.address == address)
                return
            if (sessions[address] == null) {
                Log.e("TAG", "newDevice.name = ${newDevice.name}")
                val newSession = Ailens(context, newDevice, null)
                sessions[address] = newSession
                collect(newSession)
                _updateFlow.tryEmit(Unit)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
        }
    }

    fun getSession(address: String) : Glasses? {
        return sessions[address]
    }

    /**
     * Start BLE scanning for devices.
     * Requires BLUETOOTH_SCAN permission (Android 12+) or ACCESS_FINE_LOCATION (older versions).
     *
     * @param onlyAiLens If true, only scan for AiLens devices using manufacturer filter
     */
    fun startScan(onlyAiLens: Boolean = true) {
        val adapter = bluetoothAdapter ?: run {
            Log.e(TAG, "BluetoothAdapter not available")
            return
        }

        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }

        val scanner = adapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner not available")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Clean up old sessions before starting new scan
        cleanupSessions()

        if (onlyAiLens) {
            val filters = listOf(
                ScanFilter.Builder()
                    .setManufacturerData(5378, byteArrayOf(0x00, 0x00))
                    .build()
            )
            scanner.startScan(filters, settings, scanCallback)
        } else {
            scanner.startScan(null, settings, scanCallback)
        }
        Log.e(TAG, "startScan(onlyAiLens=$onlyAiLens)")
    }

    private fun cleanupSessions() {
        Log.e(TAG, "Cleaning up ${sessions.size} sessions and ${sessionJobs.size} jobs")

        // Cancel all monitoring jobs
        sessionJobs.values.forEach { it.cancel() }
        sessionJobs.clear()

        // Disconnect all sessions
        sessions.values.forEach { session ->
            try {
                session.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting session: ${e.message}")
            }
        }
        sessions.clear()
    }

    fun stopScan() {
        Log.e(TAG, "stopScan")
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    /**
     * Retrieve and connect to previously paired device.
     * Requires BLUETOOTH_CONNECT permission (Android 12+).
     */
    fun retrieve() {
        // Cancel any pending reconnect job
        reconnectJob?.cancel()
        reconnectJob = null

        val adapter = bluetoothAdapter ?: run {
            Log.e(TAG, "BluetoothAdapter not available for retrieve")
            return
        }
        val info = SharedPrefs.getDeviceInfo(context) ?: return
        val device = try { adapter.getRemoteDevice(info.mac) } catch (_: IllegalArgumentException) { null } ?: return
        Log.e(TAG, "retrieve() creating new DeviceSession for device=${device.address}")
        val newSession = Ailens(context, device, info.retrieveToken)
        Log.e(TAG, "retrieve() created newSession=$newSession, setting as connectedSession")
        _connectedSession.value = newSession
        collect(newSession)
        _updateFlow.tryEmit(Unit)

        newSession.connect()
    }

    /**
     * Clear connected session to allow re-scanning.
     * Call this after unbinding/disconnecting device.
     */
    fun clearSession() {
        Log.d(TAG, "Clearing BLE session")
        reconnectJob?.cancel()
        reconnectJob = null
        _connectedSession.value = null
        sessions.clear()
        sessionJobs.values.forEach { it.cancel() }
        sessionJobs.clear()
    }

    private fun collect(session: Glasses) {
        val address = session.device.address

        // Cancel previous job for this address if exists
        sessionJobs[address]?.cancel()

        // Start new monitoring job
        val job = scope.launch {
            session.state.collect {
                if (it == Glasses.State.CONNECTED) {
                    sessions.remove(address)
                    // Update connectedSession to this newly connected session
                    _connectedSession.value = session
                    _updateFlow.tryEmit(Unit)
                    Log.d(TAG, "Device connected: $address")
                } else if (it == Glasses.State.DISCONNECTED) {
                    Log.e(TAG, "Device disconnected: $address, scheduling reconnect...")
                    // Cancel this job first to avoid conflict
                    sessionJobs.remove(address)?.cancel()
                    // Schedule reconnect and track the job
                    reconnectJob?.cancel()  // Cancel any existing reconnect job
                    reconnectJob = scope.launch {
                        delay(500)
                        retrieve()
                    }
                }
            }
        }

        sessionJobs[address] = job
        Log.d(TAG, "Started monitoring session: $address (total: ${sessionJobs.size})")
    }
}