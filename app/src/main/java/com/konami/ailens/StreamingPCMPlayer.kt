package com.konami.ailens

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StreamingPCMPlayer(
    private val sampleRate: Int,
    private val channelCount: Int
) {
    private val bufferQueue = Channel<ShortArray>(capacity = Channel.UNLIMITED)
    private var isClosed = false

    // Calculate channel mask
    private val channelMask: Int =
        if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

    val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        channelMask,
        AudioFormat.ENCODING_PCM_16BIT
    )

    val audioTrack: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build().apply {
            play()
        }

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            for (data in bufferQueue) {
                if (!isClosed) {
                    audioTrack.write(data, 0, data.size)
                }
            }
        }
    }

    /** Write short array directly */
    fun write(data: ShortArray) {
        bufferQueue.trySend(data)
    }

    /** Write PCM16 byte array, converts to ShortArray (little endian) */
    fun write(data: ByteArray) {
        val shortBuffer = ByteBuffer.wrap(data)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val shortArray = ShortArray(shortBuffer.remaining())
        shortBuffer.get(shortArray)
        write(shortArray)
    }

    /** Reset player and clear queued buffers */
    fun reset() {
        audioTrack.pause()
        audioTrack.flush()
        while (!bufferQueue.isEmpty) {
            bufferQueue.tryReceive().getOrNull()
        }
        audioTrack.play()
    }

    fun close() {
        isClosed = true
        bufferQueue.close()
        scope.cancel()
        audioTrack.stop()
        audioTrack.release()
    }

    fun play() {
        audioTrack.play()
    }
}
