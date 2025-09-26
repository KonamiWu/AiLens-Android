package com.konami.ailens.recorder

import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.command.ToggleMicCommand
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class BluetoothRecorder(
    private val session: DeviceSession
) : Recorder {

    private val channel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    override val frames: Flow<ByteArray> = channel.receiveAsFlow()

    private var isRecording = false

    override fun startRecording() {
        if (isRecording) return

        // reset channel
        if (!channel.isEmpty) {
            channel.tryReceive().getOrNull() // 清掉殘留
        }

        isRecording = true

        // 當 DeviceSession 收到 streaming data 時，送進 channel
        session.onStreamData = { data ->
            if (isRecording) {
                channel.trySend(data).isSuccess
            }
        }

        // 發送開啟麥克風的 BLE 指令 (你需要自己寫 OpenMicCommand 對應的 BLECommand)
        session.add(ToggleMicCommand(true))
    }

    override fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        // 發送關閉麥克風的 BLE 指令
        session.add(ToggleMicCommand(false))

        // 停止接收
        session.onStreamData = null
    }

    override fun close() {
        if (isRecording) stopRecording()
        channel.close()
    }
}