package com.konami.ailens.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.net.wifi.aware.Characteristics
import android.os.Build
import android.util.Log
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.TokenManager
import com.konami.ailens.ble.command.ActionCommand
import com.konami.ailens.ble.command.BLECommand
import com.konami.ailens.ble.command.DisconnectCommand
import com.konami.ailens.ble.command.LeaveNavigationCommand
import com.konami.ailens.navigation.NavigationService
import com.konami.ailens.orchestrator.capability.DeviceEventCapability
import com.konami.ailens.startsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class DeviceSession(private val context: Context, val device: BluetoothDevice, private val retrieveToken: ByteArray? = null) {
    sealed class State(val description: String) {
        object AVAILABLE : State("Available")
        object CONNECTING : State("Connecting")
        object CONNECTED : State("Connected")
        object PAIRING : State("Pairing")
        object DISCONNECTED : State("Disconnected")
    }

    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // === UUIDs ===
    private val writeCharacteristicUUID = UUID.fromString("00010001-0000-1000-8000-00805F9B5A6B")
    private val txCharacteristicUUID = UUID.fromString("00010002-0000-1000-8000-00805F9B5A6B")
    private val handShakingDataUUID = UUID.fromString("00010003-0000-1000-8000-00805F9B5A6B")
    private val streamingDataUUID = UUID.fromString("00030002-0000-1000-8000-00805F9B5A6B")
    private val descriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // === Characteristics ===
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var handShakingDataCharacteristic: BluetoothGattCharacteristic? = null
    private var streamingCharacteristic: BluetoothGattCharacteristic? = null

    private val descriptorQueue = ArrayDeque<BluetoothGattDescriptor>()
    private var handShakingStart = false
    private var handshakeRetryCount = 0
    private val maxHandshakeRetries = 3

    // Command executor wired to the current session (matches Swift's design)
    private val commandExecutor = CommandExecutor(this)

    // External audio/stream consumer
    var onStreamData: ((ByteArray) -> Unit)? = null
    // Protocol constants
    private val glassShowLongPressCommand = byteArrayOf(0x45, 0x4D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val connectSucceedResponseCommand = byteArrayOf(0x4F, 0x42, 0x68, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val getPhoneSystemModelCommand = byteArrayOf(0x45, 0x4D, 0x67, 0x00, 0x01, 0x00, 0x02, 0x00, 0x01)
    private val receiveLongPressCheck = byteArrayOf(0x4F, 0x42, 0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00)

    private val userPressPairingCheck = "454D6800"
    private var deviceToken: ByteArray? = null
    private var userId = 23412u
    private val cloudToken = (((System.currentTimeMillis() / 1000).toUInt() and 0x00FFFFFFu) or 0xE0000000u)

    private val enterAgentCommand = "454dCC000100020001"
    private val leaveAgentCommand = "454DCD000100020001"
    private val cancelPairCommand1 = "4F420000010002001C"
    private val cancelPairCommand2 = "4F4200000100020014"
    private val batteryPrefix= "454D100002000400"
    private val batteryPrefix2= "4F42100003000600"
    private val leaveNavigationCommand = "454D8F0012002400"
    var deviceEventHandler: DeviceEventCapability? = null

    private val _batteryFlow = MutableStateFlow<Pair<Int, Boolean>>(Pair(0, false))
    val batteryFlow = _batteryFlow.asStateFlow()
    var mtu = 23
        private set
    private val _state = MutableStateFlow<State>(State.AVAILABLE)
    val state = _state.asStateFlow()

    // Lock to prevent concurrent writes
    private val writeLock = Any()
    private var lastWriteTime = 0L
    private val minWriteInterval = 50L // ms between writes

    fun connect() {
        Log.e(TAG, "connect() called, session=$this, device=${device.address}")
        _state.value = State.CONNECTING
        gatt = device.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    private fun cleanup() {
        Log.e(TAG, "cleanup() called, session=$this")
        // This should only be called from onConnectionStateChange or connect()
        gatt?.close()
        gatt = null

        // Clear pending commands to avoid executing on stale connection
        commandExecutor.removeAllCommands()
        commandExecutor.destroy()


        // Reset state
        writeCharacteristic = null
        txCharacteristic = null
        handShakingDataCharacteristic = null
        streamingCharacteristic = null
        descriptorQueue.clear()
        handShakingStart = false
        handshakeRetryCount = 0
    }

    /**
     * Queue a BLE command (matches Swift: commandExecuter.add(command:))
     */
    fun add(command: BLECommand<*>) {
        commandExecutor.add(command)
    }

    fun add(action: () -> Unit) {
        val command = ActionCommand(action)
        add(command)
    }

    fun stopCommands() {
        commandExecutor.removeAllCommands()
    }


    fun sendRaw(bytes: ByteArray) {
        val characteristic = writeCharacteristic ?: return
        writeCharacteristic(characteristic, bytes)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.e(TAG, "onConnectionStateChange: status=$status, newState=$newState, session=$this@DeviceSession")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                if (retrieveToken == null) {
                    _state.value = State.CONNECTING
                }
                gatt?.requestMtu(512)
                scope.launch {
                    delay(500)
                    gatt?.discoverServices()
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                _state.value = State.DISCONNECTED
                // Now it's safe to close and cleanup
                cleanup()
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.DISCONNECTED
                cleanup()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.DISCONNECTED
                return
            }
            Log.e(TAG, "onServicesDiscovered: session=$this")
            var foundHandshaking = false

            gatt.services?.forEach { service ->
                service.characteristics.forEach { characteristic ->
                    when (characteristic.uuid) {
                        writeCharacteristicUUID -> writeCharacteristic = characteristic
                        txCharacteristicUUID -> txCharacteristic = characteristic
                        handShakingDataUUID -> {
                            handShakingDataCharacteristic = characteristic
                            foundHandshaking = true
                            Log.e(TAG, "Found handShakingDataCharacteristic")
                        }
                        streamingDataUUID -> {
                            streamingCharacteristic = characteristic
                            Log.e(TAG, "Found streamingCharacteristic, properties=${characteristic.properties}")
                        }
                    }
                    if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        val notifyEnabled = gatt.setCharacteristicNotification(characteristic, true)
                        Log.e(TAG, "setCharacteristicNotification for ${characteristic.uuid}: $notifyEnabled")
                        characteristic.getDescriptor(descriptorUUID)?.let { descriptor ->
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            descriptorQueue.add(descriptor)
                            Log.e(TAG, "Added descriptor to queue for ${characteristic.uuid}")
                        } ?: Log.e(TAG, "No descriptor found for ${characteristic.uuid}")
                    }
                }
            }
            Log.e(TAG, "Total descriptors in queue: ${descriptorQueue.size}")

            // iOS-style: start handshaking immediately when found, like iOS does
            if (foundHandshaking && !handShakingStart) {
                Log.e(TAG, "Starting handshaking immediately (iOS-style)")
                startHandshaking()
            }

            // Still write descriptors in parallel
            writeNextDescriptor()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.e(TAG, "onDescriptorWrite: characteristic=${descriptor.characteristic.uuid}, status=$status, remaining=${descriptorQueue.size}")

            // Just continue writing the next descriptor
            // Handshaking is already started in onServicesDiscovered (iOS-style)
            if (descriptorQueue.isNotEmpty()) {
                writeNextDescriptor()
            } else {
                Log.e(TAG, "All descriptors written")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
//            Log.e(TAG, "onCharacteristicChanged: uuid=${characteristic.uuid}, size=${value.size}, session=$this")
            handleCharacteristicValue(characteristic.uuid, value)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicValue(characteristic.uuid, value)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == handShakingDataUUID) {
                scope.launch {
                    delay(300)
                    gatt.readCharacteristic(handShakingDataCharacteristic)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            this@DeviceSession.mtu = mtu
        }
    }

    private fun writeNextDescriptor() {
        val descriptor = descriptorQueue.removeFirstOrNull() ?: return
        writeDescriptor(descriptor)
    }

    /**
     * Start initial SE handshaking sequence.
     */
    private fun startHandshaking() {
        val gatt = this@DeviceSession.gatt ?: return
        val characteristic = handShakingDataCharacteristic ?: run {
            _state.value = State.DISCONNECTED
            return
        }
        handShakingStart = true

        val token = retrieveToken ?: TokenManager().createConnectionToken(userId, cloudToken)
        characteristic.value = token
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt.writeCharacteristic(characteristic)

        scope.launch {
            delay(300)
            gatt.readCharacteristic(handShakingDataCharacteristic)
        }
    }

    private fun handleCharacteristicValue(uuid: UUID, value: ByteArray) {
        when (uuid) {
            handShakingDataUUID -> handleHandshakeValue(value)
            txCharacteristicUUID -> handleTxValue(value)
            streamingDataUUID -> { // Strip the 8-byte header if present and forward payload
                if (value.size > 8) {
                    onStreamData?.invoke(value.copyOfRange(8, value.size))
                }
            }
        }
    }

    /**
     * Parse SE handshake responses.
     */
    private fun handleHandshakeValue(value: ByteArray) {
        when {
            (value.size == 18 && value[0] == 0x64.toByte()) -> {
                deviceToken = value.copyOfRange(12, 16)
                txCharacteristic?.let { tx ->
                    gatt?.setCharacteristicNotification(tx, true)
                    val descriptor = tx.getDescriptor(descriptorUUID)
                    if (descriptor != null) {
                        writeDescriptor(descriptor)
                    }
                }
            }

            (value.size == 18 && value[0] == 0x01.toByte()) -> {
                Log.e(TAG, "Handshaking error (0x01)")
                retryHandshake()
            }

            else -> {
                Log.e(TAG, "Invalid SE value len=${value.size} first=${"%02x".format(value.firstOrNull() ?: -1)}")
                retryHandshake()
            }
        }
    }

    /**
     * Handle TX characteristic notifications: pairing flow, IMU, and command completions.
     */
    private fun handleTxValue(value: ByteArray) {
        val write = writeCharacteristic ?: return
        Log.e("TAG", "value = ${value.hexString()}, $this")
        when { // Device asks to show long-press-to-pair
            value.startsWith(cancelPairCommand1) || value.startsWith(cancelPairCommand2) -> {
                _state.value = State.AVAILABLE
                cleanup()
            }
            value.size == 1 && value[0] == 0x01.toByte() -> {
                if (retrieveToken == null) {
                    _state.value = State.PAIRING
                    scope.launch {
                        delay(300)
                        sendRaw(glassShowLongPressCommand)
                    }
                } else {
                    // Retrieve path: already paired device with token
                    Log.e("TAG", "Retrieve: device already paired")
                    scope.launch {
                        delay(500)
                        add {
                            sendRaw(getPhoneSystemModelCommand)
                        }
                        delay(500)
                        add {
                            sendRaw(getPhoneSystemModelCommand)
                        }
                        // Wait for command to complete (CommandExecutor timeout is 300ms)
                        // Add extra buffer to ensure the command is fully processed
                        Log.e("TAG", "Setting CONNECTED state after init command completed")
                        _state.value = State.CONNECTED
                    }
                }
            }

            // user Long press confirmed on glasses
            value.size == 9 && value.contentEquals(receiveLongPressCheck) -> {
                add {
                    sendRaw(getPhoneSystemModelCommand)
                }
            }

            value.size == 9 && value.startsWith(userPressPairingCheck) -> {
                val ok = value.lastOrNull()?.toInt() == 0x01
                if (ok && deviceToken != null) {
                    SharedPrefs.saveDeviceInfo(context, device.address, userId, deviceToken!!)
                }


                if (ok) {
                    scope.launch {
                        delay(300)
                        add {
                            sendRaw(connectSucceedResponseCommand)
                        }
                        add {
                            sendRaw(getPhoneSystemModelCommand)
                        }

                    }
                    _state.value = State.CONNECTED
                }
                else
                    _state.value = State.AVAILABLE
            }

            value.startsWith(enterAgentCommand) -> {
                Log.e("TAG", "DeviceSession EnterAgent")
                deviceEventHandler?.handleDeviceEvent(DeviceEventCapability.DeviceEvent.EnterAgent)
            }

            value.startsWith(leaveAgentCommand) -> {
                Log.e("TAG", "DeviceSession LeaveAgent")
                deviceEventHandler?.handleDeviceEvent(DeviceEventCapability.DeviceEvent.LeaveAgent)
            }

            value.startsWith(batteryPrefix) -> {
                val batteryValue = value[value.size - 2].toInt()
                val charging = value[value.size - 1].toInt()
                _batteryFlow.value = Pair(batteryValue, charging == 1)
            }

            value.startsWith(batteryPrefix2) -> {
                val batteryValue = value[value.size - 2].toInt()
                val charging = value[value.size - 1].toInt()
                _batteryFlow.value = Pair(batteryValue, charging == 1)
            }

            value.startsWith(leaveNavigationCommand) -> {
                deviceEventHandler?.handleDeviceEvent(DeviceEventCapability.DeviceEvent.LeaveNavigation)
            }

            // TX write ack / generic command completion header "0x4F 0x42"
            // → Complete the pending command with raw `value`.
            value.startsWith(byteArrayOf(0x4F, 0x42)) -> {
                commandExecutor.next(value)
            }
        }
    }

    /**
     * Retry handshake with small delay and capped attempts.
     */
    private fun retryHandshake() {
        scope.launch {
            delay(300)
            handshakeRetryCount++
            if (handshakeRetryCount < maxHandshakeRetries) {
                startHandshaking()
            } else {
                _state.value = State.DISCONNECTED
            }
        }
    }

    private fun writeDescriptor(descriptor: BluetoothGattDescriptor) {
        val gatt = gatt ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val gatt = gatt ?: return

        synchronized(writeLock) {
            // Throttle writes to prevent "prior command is not finished" error
            val now = System.currentTimeMillis()
            val elapsed = now - lastWriteTime
            if (elapsed < minWriteInterval) {
                Thread.sleep(minWriteInterval - elapsed)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = value
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }

            lastWriteTime = System.currentTimeMillis()
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (prefix.size > this.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.hexString(): String {
        return joinToString(" ") { "%02X ".format(it) }
    }

    companion object {
        private const val TAG = "DeviceSession"
    }

    private fun ByteArray.readUInt16LE(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8)
    }
}
