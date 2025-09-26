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
            get() = _instance ?: throw IllegalStateException("Call BLEService.init(context) or getOrCreate(context) first")

        fun init(context: Context) {
            if (_instance == null) {
                synchronized(this) {
                    if (_instance == null) {
                        _instance = BLEService(context.applicationContext)
                    }
                }
            }
        }

        fun getOrCreate(context: Context): BLEService {
            return _instance ?: synchronized(this) {
                _instance ?: BLEService(context.applicationContext).also { _instance = it }
            }
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _updateFlow = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val updateFlow = _updateFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    val sessions = mutableMapOf<String, DeviceSession>()
    private val _connectedSession = MutableStateFlow<DeviceSession?>(null)
    val connectedSession = _connectedSession.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val newDevice = result.device ?: return
            val address = newDevice.address ?: return
            if (_connectedSession.value?.device?.address == address)
                return

            if (sessions[address] == null) {
                val newSession = DeviceSession(context, newDevice, null)
                sessions[address] = newSession
                collect(newSession)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
        }
    }

    //TODO: remove this function
    fun getSession(address: String) : DeviceSession? {
        return sessions[address]
    }

    fun startScan(onlyAiLens: Boolean = true) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

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

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        Log.e(TAG, "stopScan")
    }

    fun retrieve() {
        val adapter = bluetoothAdapter ?: return
        val info = SharedPrefs.getDeviceInfo(context) ?: return
        val device = try { adapter.getRemoteDevice(info.mac) } catch (_: IllegalArgumentException) { null } ?: return
        val isBonded = adapter.bondedDevices?.any { it.address == device.address } == true

        val newSession: DeviceSession
        if (isBonded) {
            newSession = DeviceSession(context, device, info.retrieveToken)
            _connectedSession.value = newSession
        } else {
            newSession = DeviceSession(context, device, null)
            _connectedSession.value = newSession
        }
        collect(newSession)
        _updateFlow.tryEmit(Unit)

        newSession.connect()
    }

    private fun collect(session: DeviceSession) {
        scope.launch {
            session.state.collect {
                if (it == DeviceSession.State.CONNECTED) {
                    sessions.remove(session.device.address)
                    if (_connectedSession.value == null)
                        _connectedSession.value = session
                }
                _updateFlow.tryEmit(Unit)
            }
        }
    }
}