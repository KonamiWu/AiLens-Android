package com.konami.ailens.recorder

import android.util.Log
import com.konami.ailens.ble.Glasses
import com.konami.ailens.ble.command.ToggleAgentMicCommand
import com.konami.ailens.ble.command.ToggleMicCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class BluetoothRecorder(
    private val session: Glasses, private val isAgent: Boolean
) : Recorder {

    init {
        Log.e("BluetoothRecorder", "BluetoothRecorder created with session=$session")
    }

    private val channel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    override val frames: Flow<ByteArray> = channel.receiveAsFlow()

    private var isRecording = false

    override fun startRecording() {
        if (isRecording) return
        // reset channel
        if (!channel.isEmpty) {
            channel.tryReceive().getOrNull()
        }

        isRecording = true

        session.onStreamData = { data ->
            if (isRecording) {
                channel.trySend(data).isSuccess
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            delay(300)
            if (isAgent)
                session.add(ToggleAgentMicCommand(true))
            else
                session.add(ToggleMicCommand(true))
        }
    }

    override fun stopRecording() {
        if (isAgent)
            session.add(ToggleAgentMicCommand(false))
        else
            session.add(ToggleMicCommand(false))

        if (!isRecording)
            return

        isRecording = false
        session.onStreamData = null
    }

    override fun close() {
        if (isRecording) {
            stopRecording()
        }

        channel.close()
    }
}