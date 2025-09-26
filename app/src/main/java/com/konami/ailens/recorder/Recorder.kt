package com.konami.ailens.recorder

import kotlinx.coroutines.flow.Flow

interface Recorder {
    val frames: Flow<ByteArray>

    fun startRecording()

    fun stopRecording()

    fun close()
}