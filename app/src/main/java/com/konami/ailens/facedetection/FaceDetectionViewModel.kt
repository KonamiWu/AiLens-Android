package com.konami.ailens.facedetection

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.HeadTracker
import com.konami.ailens.ble.command.ClearCanvasCommand
import com.konami.ailens.ble.command.DrawRectCommand
import com.konami.ailens.ble.command.DrawTextBLECommand
import com.konami.ailens.ble.command.OpenCanvasCommand
import com.konami.ailens.ble.command.SubscribeIMUCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.collections.listOf
import kotlin.collections.map

class FaceDetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val tracker = HeadTracker(
        screenWidth = 640,
        screenHeight = 480,
        hfovDeg = 20.0,
        vfovDeg = 15.0,
    )

    private val rectW = 100
    private val rectH = 100

    private var lastDrawMs = 0L
    private val drawIntervalMs = 200L
    private var lastSentX = Int.MIN_VALUE
    private var lastSentY = Int.MIN_VALUE

    val repo by lazy { FaceRepository() }
    private val recognizer by lazy { OnnxFaceRecognizer(application) }

    var transformMatrix = Matrix()
    private var faceResult = listOf<FaceAnalyzer.RecognizedFace>()

    private val _ready = MutableStateFlow<Boolean>(false)
    val readyFlow = _ready.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
//            AssetFaceLoader.loadToRepository(application, repo, recognizer)
            AssetFaceLoader.loadEmbeddingsFromAssets(application, repo)
            _ready.emit(true)
        }

        val aiLens = BLEService.instance.lastDevice.value!!
        val session = BLEService.instance.getSession(aiLens.device.address)!!
        session.add(OpenCanvasCommand(session))
        session.add(SubscribeIMUCommand(session))
    }

    fun updateFaceResult(faceResult: List<FaceAnalyzer.RecognizedFace>) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDrawMs < drawIntervalMs)
            return

        lastDrawMs = now

        var isTheSame = areFaceListsEqualByName(this.faceResult, faceResult)
        if (isTheSame)
            return
        this.faceResult = faceResult

        val aiLens = BLEService.instance.lastDevice.value!!
        val session = BLEService.instance.getSession(aiLens.device.address)!!

        session.add(ClearCanvasCommand(session))

        var result = ""
        var unknown = 0
        faceResult.forEach {
            val name = it.name
            if (name == "unknown") {
                unknown ++
            } else {
                result += name
                result += "\n"
            }
        }

        if (unknown != 0)
            result += "Unknown: %d".format(result)
        val command = DrawTextBLECommand(session, result, 0, 0, 100, 100)
        session.add(command)
    }

    fun resetCenter() {
        tracker.recenter()
    }

    fun recalibrate() {
        tracker.restartCalibration()
    }

    private fun areFaceListsEqualByName(list1: List<FaceAnalyzer.RecognizedFace>, list2: List<FaceAnalyzer.RecognizedFace>): Boolean {
        if (list1.size != list2.size) return false
        val names1 = list1.map { it.name }.sorted()
        val names2 = list2.map { it.name }.sorted()
        return names1 == names2
    }
}