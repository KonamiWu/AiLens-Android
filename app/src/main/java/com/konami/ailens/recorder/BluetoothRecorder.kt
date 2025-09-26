package com.konami.ailens.recorder

import android.util.Log
import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.command.ToggleMicCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

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

        session.onStreamData = { data ->
            if (isRecording) {
                channel.trySend(data).isSuccess
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            delay(300)
            session.add(ToggleMicCommand(true))
        }
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