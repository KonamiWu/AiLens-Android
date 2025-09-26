package com.konami.ailens.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PhoneRecorder(
    private val sampleRate: Int = 16000,
    private val bufferSize: Int = AudioRecord.getMinBufferSize(
        16000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
) : Recorder {

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )

    private val channel = Channel<ByteArray>(capacity = Channel.BUFFERED)
    override val frames: Flow<ByteArray> = channel.receiveAsFlow()

    private var recordingScope: CoroutineScope? = null

    override fun startRecording() {
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED)
            return
        audioRecord.startRecording()
        recordingScope = CoroutineScope(Dispatchers.IO).apply {
            launch {
                val buffer = ByteArray(bufferSize)
                while (isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val frame = buffer.copyOf(read)
                        channel.send(frame)
                    }
                }
            }
        }
    }

    override fun stopRecording() {
        audioRecord.stop()
        recordingScope?.coroutineContext?.cancel()
        recordingScope = null
    }

    override fun close() {
        stopRecording()
        audioRecord.release()
        channel.close()
    }
}
