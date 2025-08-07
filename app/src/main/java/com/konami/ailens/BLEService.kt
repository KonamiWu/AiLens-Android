package com.konami.ailens

import AiLens
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
class BLEService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BLEService"

        @SuppressLint("StaticFieldLeak")
        private lateinit var _instance: BLEService
        val instance: BLEService
            get() = _instance

        fun init(context: Context) {
            if (!::_instance.isInitialized) {
                _instance = BLEService(context.applicationContext)
            }
        }

        fun isInitialized(): Boolean {
            return ::_instance.isInitialized
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _lastDevice = MutableStateFlow<AiLens?>(null)
    val lastDevice: StateFlow<AiLens?> = _lastDevice

    private val _availableDevices = MutableStateFlow<List<AiLens>>(emptyList())
    val availableDevices: StateFlow<List<AiLens>> = _availableDevices

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (_availableDevices.value.none { it.device.address == device.address } &&
                _lastDevice.value?.device?.address != device.address) {

                val lenses = AiLens(context, device, retrieveToken = null)
                val newList = _availableDevices.value.toMutableList().apply { add(lenses) }
                _availableDevices.value = newList
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }

    init {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not available on this device")
        }
    }

    fun scan() {
        val bluetoothAdapter = bluetoothAdapter ?: return
        if (bluetoothAdapter.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "Bluetooth LE scanner not available")
            return
        }

        // 建議根據 AiLens 服務 UUID 過濾裝置，以下是示例：
        val filter = ScanFilter.Builder()
            .setManufacturerData(5378, byteArrayOf(0x00, 0x00))
            .build()
        val filters = listOf(filter)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, scanCallback)
        Log.e(TAG, "Started BLE scan")
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        Log.e(TAG, "Stopped BLE scan")
    }

    fun retrieve() {
        val bluetoothAdapter = bluetoothAdapter ?: return
        if (bluetoothAdapter.isEnabled != true) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }

        val lastDeviceInfo = SharedPrefs.getDeviceInfo(context) ?: run {
            Log.w(TAG, "No last device info found")
            return
        }

        val device = try {
            bluetoothAdapter.getRemoteDevice(lastDeviceInfo.mac)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid MAC address: ${lastDeviceInfo.mac}")
            return
        } ?: return

        // 依照你現有 AiLens 建構子改寫
        val isNotBonded = bluetoothAdapter.bondedDevices?.none { it.address == device.address } == true
        val aiLens = AiLens(context, device, retrieveToken = if (isNotBonded) null else lastDeviceInfo.retrieveToken)

        // 設定狀態為連線中 (可改成 AiLens 內部實作的狀態流程)
//        aiLens.setState(AiLens.State.CONNECTING)

        _lastDevice.value = aiLens

        aiLens.connect()
    }

    fun setLastDevice(aiLens: AiLens) {
        _lastDevice.value = aiLens
    }
}
