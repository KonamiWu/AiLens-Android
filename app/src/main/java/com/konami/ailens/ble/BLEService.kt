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
import com.konami.ailens.ble.command.BLECommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.collections.plus


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

        fun isInitialized(): Boolean = _instance != null
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _devices = MutableStateFlow<List<AiLens>>(emptyList())
    val devices: StateFlow<List<AiLens>> = _devices.asStateFlow()

    private val _lastDevice = MutableStateFlow<AiLens?>(null)
    val lastDevice: StateFlow<AiLens?> = _lastDevice.asStateFlow()

    private val sessions = mutableMapOf<String, DeviceSession>()

    fun connectedDevices(): List<AiLens> = _devices.value.filter { it.state == AiLens.State.CONNECTED }
    fun currentConnected(): AiLens? = _devices.value.firstOrNull { it.state == AiLens.State.CONNECTED }
    fun isConnected(address: String): Boolean = _devices.value.any { it.device.address == address && it.state == AiLens.State.CONNECTED }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            if (_devices.value.any { it.device.address == address }) return
            _devices.value = _devices.value + AiLens(device, AiLens.State.AVAILABLE)
        }
        override fun onScanFailed(errorCode: Int) { Log.e(TAG, "BLE scan failed: $errorCode") }
    }

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
                    // .setServiceUuid(ParcelUuid.fromString("00010000-0000-1000-8000-00805F9B5A6B"))
                    .build()
            )
            scanner.startScan(filters, settings, scanCallback)
        } else {
            scanner.startScan(null, settings, scanCallback)
        }
        Log.d(TAG, "startScan(onlyAiLens=$onlyAiLens)")
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        Log.d(TAG, "stopScan")
    }

    fun connect(address: String) {
        val adapter = bluetoothAdapter ?: return
        val device = try { adapter.getRemoteDevice(address) } catch (_: IllegalArgumentException) { null } ?: return
        setDeviceState(address, AiLens.State.CONNECTING)
        val session = sessions.getOrPut(address) {
            DeviceSession(context, device) { newState ->
                setDeviceState(address, newState)
                if (newState == AiLens.State.CONNECTED) {
                    _lastDevice.value = _devices.value.firstOrNull { it.device.address == address }
                }
            }
        }
        session.connect()
    }

    fun disconnect(address: String) {
        sessions[address]?.disconnect()
        setDeviceState(address, AiLens.State.DISCONNECTED)
    }

    fun retrieve() {
        val adapter = bluetoothAdapter ?: return
        val info = SharedPrefs.getDeviceInfo(context) ?: return
        val device = try { adapter.getRemoteDevice(info.mac) } catch (_: IllegalArgumentException) { null } ?: return
        val isBonded = adapter.bondedDevices?.any { it.address == device.address } == true

        if (_devices.value.none { it.device.address == device.address }) {
            val targetDevice = AiLens(device, AiLens.State.CONNECTING)
            _devices.value = listOf(targetDevice) + _devices.value
        }
        _lastDevice.value = _devices.value.firstOrNull { it.device.address == device.address }

        val session = sessions.getOrPut(device.address) {
            DeviceSession(context, device, retrieveToken = if (isBonded) info.retrieveToken else null) { newState ->
                val newDevice = AiLens(device, newState)
                val temp = _devices.value.toMutableList()
                temp.removeIf { it.device.address == device.address }
                _lastDevice.value = newDevice
                temp.add(newDevice)
                _devices.value = temp
            }
        }
        session.connect()
    }

    fun addCommand(address: String, command: BLECommand) {
        sessions[address]?.add(command)
    }

    fun stopCommands(address: String) {
        sessions[address]?.stopCommands()
    }

    fun sendRaw(address: String, bytes: ByteArray) {
        sessions[address]?.sendRaw(bytes)
    }

    fun setStreamNotifyOn(address: String) {
        sessions[address]?.setStreamNotifyOn()
    }

    fun setOnStreamData(address: String, cb: (ByteArray) -> Unit) {
        sessions[address]?.onStreamData = cb
    }

    private fun setDeviceState(address: String, newState: AiLens.State) {
        val updated = _devices.value.map {
            if (it.device.address == address) it.copy(state = newState) else it
        }
        _devices.value = if (updated.any { it.device.address == address }) updated
        else updated + (bluetoothAdapter?.getRemoteDevice(address)?.let { AiLens(it, newState) } ?: return)
    }
}