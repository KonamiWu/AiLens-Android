import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.konami.ailens.BLECommand
import com.konami.ailens.CommandExecutor
import com.konami.ailens.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

@SuppressLint("MissingPermission")
class AiLens(private val context: Context, val device: BluetoothDevice, private val retrieveToken: ByteArray?) {
    enum class State(val value: String) {
        DISCONNECTED("Disconnected"),
        CONNECTING("Connecting"),
        CONNECTED("Connected"),
        PAIRING("Pairing"),
        AVAILABLE("Available")
    }

    data class MetaGlassesState(
        val state: State,
        val aiLens: AiLens
    )

    var gatt: BluetoothGatt? = null
        private set
    private val _stateFlow = MutableStateFlow(MetaGlassesState(State.AVAILABLE, this))
    val stateFlow: StateFlow<MetaGlassesState> = _stateFlow
    var state: MetaGlassesState
        get() = _stateFlow.value
        set(value) {
            _stateFlow.value = value
        }
    var writeCharacteristic: BluetoothGattCharacteristic? = null
        private set
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    var streamingCharacteristic: BluetoothGattCharacteristic? = null
        private set
    private var handShakingDataCharacteristic: BluetoothGattCharacteristic? = null

    // UUIDs
    private val serviceUUID = UUID.fromString("00010000-0000-1000-8000-00805F9B5A6B")
    private val writeCharacteristicUUID = UUID.fromString("00010001-0000-1000-8000-00805F9B5A6B")
    private val txCharacteristicUUID = UUID.fromString("00010002-0000-1000-8000-00805F9B5A6B")
    private val handShakingDataUUID = UUID.fromString("00010003-0000-1000-8000-00805F9B5A6B")
    private val streamingDataUUID = UUID.fromString("00030002-0000-1000-8000-00805F9B5A6B")
    private val powerLevelUUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    private val descriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    // Commands
    private val directPairCommand = byteArrayOf(0x45, 0x4D, 0x0C, 0x01, 0x00, 0x00, 0x00, 0x00,)
    private val test = byteArrayOf(0x4F, 0x42, 0x68, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val glassShowLongPressCommand = byteArrayOf(0x45, 0x4D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val subscribeLightSensorsCommand = byteArrayOf(0x45, 0x4D, 0x69, 0x00, 0x01, 0x00, 0x02, 0x00, 0x01)
    private val getPhoneSystemModelToShowPairCommand = byteArrayOf(0x45, 0x4D, 0x67, 0x00, 0x01, 0x00, 0x02, 0x00, 0x01)
    private val longPressTimeoutResponse = byteArrayOf(0x4F, 0x42, 0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x1C)
    private val longPressConfirmResponse = byteArrayOf(0x4F, 0x42, 0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00)

    private var connectionTimeoutJob: Job? = null
    private var handshakeRetryCount = 0
    private val maxHandshakeRetries = 3
    private val commandExecutor = CommandExecutor()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var handShakingStart = false
    private val userId: UInt = 23412u
    private val cloudToken: UInt = (((System.currentTimeMillis() / 1000).toUInt() and 0x00FFFFFFu) or 0xE0000000u)

    var onStreamData: ((ByteArray) -> Unit)? = null

    fun connect() {
        state = MetaGlassesState(State.CONNECTING, this)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        startConnectionTimeout()
    }

    fun stopAction() {
        commandExecutor.removeAllCommands()
    }

//    fun disconnect() {
//        state = MetaGlassesState(State.DISCONNECTED, this)
//        gatt?.disconnect()
//        gatt?.close()
//        gatt = null
//    }

    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(10_000) // 10 seconds
            if (state.state == State.CONNECTING) {
                state = MetaGlassesState(State.DISCONNECTED, this@AiLens)
            }
        }
    }

    private fun startHandshaking() {
        handShakingStart = true
        Log.e(TAG, "startHandshaking")
        val characteristic = handShakingDataCharacteristic ?: run {
            Log.e(TAG, "Handshaking characteristic not found")
            state = MetaGlassesState(State.DISCONNECTED, this)
            return
        }
        val token = retrieveToken ?: TokenManager(context).createConnectionToken(userId, cloudToken)
        characteristic.value = token
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt?.writeCharacteristic(characteristic)
        state = MetaGlassesState(State.PAIRING, this)
    }

    fun add(command: BLECommand) {
        commandExecutor.add(command)
    }

    fun setStreamNotifyOn() {
        val streamingCharacteristic = streamingCharacteristic ?: return
        gatt?.setCharacteristicNotification(streamingCharacteristic, true)
        val descriptor = streamingCharacteristic.getDescriptor(descriptorUUID)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt?.writeDescriptor(descriptor)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.e(TAG, "onConnectionStateChange: status=$status, newState=$newState, gatt=${gatt?.hashCode()}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        if (state.state != State.PAIRING && state.state != State.CONNECTED) {
                            Log.e(TAG, "Connected to GATT server.")
                            state = MetaGlassesState(State.PAIRING, this@AiLens)
                            gatt?.requestMtu(512)
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(500)
                                gatt?.discoverServices()
                            }
                        }
                    }

                    BluetoothGatt.STATE_DISCONNECTED -> {
                        Log.e(TAG, "Disconnected from GATT server.")
                        state = MetaGlassesState(State.DISCONNECTED, this@AiLens)
                    }
                }
            } else {
                Log.e(TAG, "Connection state change error: $status")
                state = MetaGlassesState(State.DISCONNECTED, this@AiLens)
            }
        }

        var descriptorList = mutableListOf<BluetoothGattDescriptor>()
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (gatt == null) return
                gatt.services?.forEach { service ->
                    service.characteristics.forEach { char ->
                        when (char.uuid) {
                            writeCharacteristicUUID -> writeCharacteristic = char
                            txCharacteristicUUID -> txCharacteristic = char
                            handShakingDataUUID -> handShakingDataCharacteristic = char
                            streamingDataUUID -> streamingCharacteristic = char
                        }
//                        Log.e(TAG, "********* Characteristic UUID = ${char.uuid} *********")
                        if ((char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            gatt.setCharacteristicNotification(char, true)
                            val descriptor = char.getDescriptor(descriptorUUID)
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            if (descriptorList.isEmpty()) {
                                gatt.writeDescriptor(descriptor)
                            }
                            descriptorList.add(descriptor)
                        }
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                state = MetaGlassesState(State.DISCONNECTED, this@AiLens)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicValue(characteristic.uuid, value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicValue(characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (characteristic?.uuid == handShakingDataUUID) {
                CoroutineScope(Dispatchers.IO).launch {
                    delay(300)
                    gatt?.readCharacteristic(handShakingDataCharacteristic)
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (descriptorList.isNotEmpty())
                descriptorList.removeAt(0)


            if (!handShakingStart && descriptorList.isEmpty()) {
                startHandshaking()
            } else {

                descriptorList.firstOrNull()?.let {
                    it.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    gatt?.writeDescriptor(it)
                }
            }
        }
    }

    private fun handleCharacteristicValue(uuid: UUID, value: ByteArray) {
        val writeCharacteristic = writeCharacteristic ?: return
        val valueString = value.toHexString()

        when (uuid) {
            handShakingDataUUID -> {
                Log.e(TAG, "SE value: $valueString")
                if (value.size == 18 && value[0] == 0x64.toByte()) {
                    Log.e(TAG, "Handshaking succeeded (18 bytes)")
                    txCharacteristic?.let {
                        gatt?.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        gatt?.writeDescriptor(descriptor)
                    }
                } else if (value.size == 18 && value[0] == 0x01.toByte()) {
                    Log.e(TAG, "Handshaking failed: Received error response (0x01), length 18")
                    retryHandshake()
                } else {
                    Log.e(
                        TAG, "Handshaking failed: Invalid SE value (length: ${value.size}, expected 18; first byte: ${
                            "%02x".format(value[0])
                        }, expected 0x64)"
                    )
                    retryHandshake()
                }
            }

            txCharacteristicUUID -> {
                Log.e(TAG, "TX value: $valueString")
                when {
                    value.size == 1 && value[0] == 0x01.toByte() -> {
                        state = MetaGlassesState(State.PAIRING, this)
                        writeCharacteristic.value = glassShowLongPressCommand
                        writeCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(300)
                            gatt?.writeCharacteristic(writeCharacteristic)
                        }
                    }
                    value.size == 9 && value.contentEquals(longPressConfirmResponse) -> {
                        writeCharacteristic.value = getPhoneSystemModelToShowPairCommand
                        gatt?.writeCharacteristic(writeCharacteristic)

                        if (retrieveToken != null) {
                            state = MetaGlassesState(State.CONNECTED, this)
                        }
                    }
                    value.size == 9 && value.startsWith(byteArrayOf(0x45, 0x4D, 0x68, 0x00)) -> { //user press confirm of system pairing dialog
                        if (value.lastOrNull()?.toInt() == 0x01) {
                            Log.e(TAG, "user press confirm of system pairing dialog")
                            state = MetaGlassesState(State.CONNECTED, this)
                        } else {
                            state = MetaGlassesState(State.AVAILABLE, this)
                        }
                    }
                    value.startsWith(byteArrayOf(0x4F, 0x42)) -> {
                        scope.launch {
                            commandExecutor.next()
                        }
                    }
                }
            }

            streamingDataUUID -> {
                if (value.size > 8) {
                    scope.launch {
                        onStreamData?.invoke(value.copyOfRange(8, value.size))
                    }
                }
            }
        }
    }

    private fun retryHandshake() {
        handshakeRetryCount++
        if (handshakeRetryCount < maxHandshakeRetries) {
            Log.e(TAG, "Retrying Handshaking...")
            startHandshaking()
        } else {
            Log.e(TAG, "MetaGlasses error: error se value")
            state = MetaGlassesState(State.DISCONNECTED, this)
        }
    }

    private fun unpair() {
        try {
            val method = device.javaClass.getMethod("removeBond")
            val result = method.invoke(device) as? Boolean
            if (result == true) {
                Log.e("BT", "bt device unpair success")
            }
        } catch (e: Exception) {
            Log.e("BT", "bt device unpair error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MetaGlasses"
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (prefix.size > this.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}