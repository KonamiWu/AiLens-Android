package com.konami.ailens.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import com.konami.ailens.SharedPrefs
import com.konami.ailens.TokenManager
import com.konami.ailens.ble.command.BLECommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@SuppressLint("MissingPermission")
class DeviceSession(
    private val context: Context,
    private val device: BluetoothDevice,
    private val retrieveToken: ByteArray? = null,
    private val onState: (AiLens.State) -> Unit
) {
    enum class IMUType(val code: Int) {
        ACCEL(0x00), GYROS(0x01), MAGNE(0x02), ALGO(0x03);
        companion object { fun from(b: Int) = IMUType.entries.firstOrNull { it.code == b } }
    }

    data class IMUSample(
        val type: IMUType,
        val timestamp: Long, // uint64 (LE)
        val x: Float,
        val y: Float,
        val z: Float
    )

    private var gatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // === UUID ===
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

    // === 指令相關 ===
    private val commandExecutor = CommandExecutor()
    var onStreamData: ((ByteArray) -> Unit)? = null

    // 你原本的協議常數（照搬）
    private val glassShowLongPressCommand = byteArrayOf(0x45, 0x4D, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val subscribeLightSensorsCommand = byteArrayOf(0x45, 0x4D, 0x69, 0x00, 0x01, 0x00, 0x02, 0x00, 0x01)
    private val getPhoneSystemModelToShowPairCommand = byteArrayOf(0x45, 0x4D, 0x67, 0x00, 0x01, 0x00, 0x02, 0x00, 0x01)
    private val longPressConfirmResponse = byteArrayOf(0x4F, 0x42, 0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00)

    private var deviceToken: ByteArray? = null
    private var userId = 23412u
    private val cloudToken = (((System.currentTimeMillis() / 1000).toUInt() and 0x00FFFFFFu) or 0xE0000000u)

    private val _imuFlow = MutableSharedFlow<IMUSample>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val imuFlow: SharedFlow<IMUSample> = _imuFlow


    fun connect() {
        onState(AiLens.State.CONNECTING)
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect(); gatt?.close(); gatt = null
        onState(AiLens.State.DISCONNECTED)
    }

    // 對外：加入佇列指令（沿用你的 CommandExecutor）
    fun add(command: BLECommand) { commandExecutor.add(command) }
    fun stopCommands() { commandExecutor.removeAllCommands() }

    // 對外：直接送 raw bytes（必要時）
    fun sendRaw(bytes: ByteArray) {
        val c = writeCharacteristic ?: return
        c.value = bytes
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt?.writeCharacteristic(c)
    }

    fun setStreamNotifyOn() {
        val c = streamingCharacteristic ?: return
        gatt?.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(descriptorUUID) ?: return
        d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt?.writeDescriptor(d)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.e(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                if (retrieveToken == null)
                    onState(AiLens.State.PAIRING)
                gatt?.requestMtu(512)
                scope.launch { delay(500); gatt?.discoverServices() }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                onState(AiLens.State.DISCONNECTED)
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                onState(AiLens.State.DISCONNECTED)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { onState(AiLens.State.DISCONNECTED); return }
            gatt.services?.forEach { s ->
                s.characteristics.forEach { c ->
                    when (c.uuid) {
                        writeCharacteristicUUID -> writeCharacteristic = c
                        txCharacteristicUUID -> txCharacteristic = c
                        handShakingDataUUID -> handShakingDataCharacteristic = c
                        streamingDataUUID -> streamingCharacteristic = c
                    }
                    if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        gatt.setCharacteristicNotification(c, true)
                        c.getDescriptor(descriptorUUID)?.let { d ->
                            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            descriptorQueue.add(d)
                        }
                    }
                }
            }
            writeNextDescriptor()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            writeNextDescriptor()
            if (!handShakingStart && descriptorQueue.isEmpty()) {
                startHandshaking()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicValue(characteristic.uuid, value)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleCharacteristicValue(characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == handShakingDataUUID) {
                scope.launch {
                    delay(300);
                    gatt.readCharacteristic(handShakingDataCharacteristic)
                }
            }
        }
    }

    private fun writeNextDescriptor() {
        val g = gatt ?: return
        val d = descriptorQueue.removeFirstOrNull() ?: return
        g.writeDescriptor(d)
    }

    private fun startHandshaking() {
        val gatt = this@DeviceSession.gatt ?: return
        val characteristic = handShakingDataCharacteristic ?: run { onState(AiLens.State.DISCONNECTED); return }
        handShakingStart = true
        val token = retrieveToken ?: TokenManager().createConnectionToken(
            userId,
            cloudToken
        )
        characteristic.value = token
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        gatt.writeCharacteristic(characteristic)
//        onState(AiLens.State.PAIRING)

        scope.launch {
            delay(300)
            gatt.readCharacteristic(handShakingDataCharacteristic)
        }
    }

    private fun handleCharacteristicValue(uuid: UUID, value: ByteArray) {

        when (uuid) {
            handShakingDataUUID -> handleHandshakeValue(value)
            txCharacteristicUUID -> handleTxValue(value)
            streamingDataUUID -> if (value.size > 8) onStreamData?.invoke(value.copyOfRange(8, value.size))
        }
    }

    private fun handleHandshakeValue(value: ByteArray) {
        val write = writeCharacteristic ?: return
        when {
            (value.size == 18 && value[0] == 0x64.toByte()) -> {
                Log.e(TAG, "Handshaking OK (0x64, len=18)")
                deviceToken = value.copyOfRange(12, 16)
                // 啟用 TX 通知（若未啟用）
                txCharacteristic?.let { tx ->
                    gatt?.setCharacteristicNotification(tx, true)
                    val d = tx.getDescriptor(descriptorUUID)
                    d?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    if (d != null) gatt?.writeDescriptor(d)
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

    private fun handleTxValue(value: ByteArray) {
        val write = writeCharacteristic ?: return
//        Log.e("TAG", "value = ${value.hexString()}")
        when {
            value.size == 1 && value[0] == 0x01.toByte() -> {
                // show long press to pair
                if (retrieveToken == null) {
                    onState(AiLens.State.PAIRING)
                    write.value = glassShowLongPressCommand
                    write.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    scope.launch { delay(300); gatt?.writeCharacteristic(write) }
                } else {
                    onState(AiLens.State.CONNECTED)
                }
            }
            value.size == 9 && value.contentEquals(longPressConfirmResponse) -> {
                // show phone system pair
                write.value = getPhoneSystemModelToShowPairCommand
                gatt?.writeCharacteristic(write)
                if (retrieveToken != null) onState(AiLens.State.CONNECTED)
            }
            value.size == 9 && value.startsWith(byteArrayOf(0x45, 0x4D, 0x68, 0x00)) -> {
                // user press confirm on system pairing dialog
                val ok = value.lastOrNull()?.toInt() == 0x01
                if (ok && deviceToken != null) {
                    SharedPrefs.saveDeviceInfo(context, device.address, userId, deviceToken!!)
                }
                onState(if (ok) AiLens.State.CONNECTED else AiLens.State.AVAILABLE)
            }
            value.startsWith(byteArrayOf(0x45, 0x4D, 0x15, 0x00)) -> {
                val payload = value.copyOfRange(8, value.size)
                parseImuPayload(payload)
            }
            value.startsWith(byteArrayOf(0x4F, 0x42)) -> {
                scope.launch { commandExecutor.next() }
            }
        }
    }

    private fun parseImuPayload(bytes: ByteArray) {
        // meta_imu_info = 1B type + 8B ts + 4B x + 4B y + 4B z (LE) = 21 bytes
        var i = 0
        while (i + 21 <= bytes.size) {
            val typeCode = bytes[i].toInt() and 0xFF
            val type = IMUType.from(typeCode)
            if (type == null) {
                i += 21;
                continue
            }
            val ts = ByteBuffer.wrap(bytes, i + 1, 8)
                .order(ByteOrder.LITTLE_ENDIAN).long

            val bb = ByteBuffer.wrap(bytes, i + 9, 12)
                .order(ByteOrder.LITTLE_ENDIAN)
            val x = bb.float
            val y = bb.float
            val z = bb.float

            val value = IMUSample(type, ts, x, y, z)
//            Log.e("TAG", "value = ${value}")
            _imuFlow.tryEmit(IMUSample(type, ts, x, y, z))
            i += 21
        }
    }

    private fun retryHandshake() {
        scope.launch {
            delay(300)
            handshakeRetryCount++
            if (handshakeRetryCount < maxHandshakeRetries) startHandshaking() else onState(AiLens.State.DISCONNECTED)
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (prefix.size > this.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun ByteArray.hexString(): String = joinToString(" ") { "%02X ".format(it) }

    companion object { private const val TAG = "DeviceSession" }
}