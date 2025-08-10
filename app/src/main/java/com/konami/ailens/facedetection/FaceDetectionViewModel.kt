package com.konami.ailens.facedetection

import android.app.Application
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.HeadTracker
import com.konami.ailens.ble.command.ClearCanvasCommand
import com.konami.ailens.ble.command.DrawRectCommand
import com.konami.ailens.ble.command.OpenCanvasCommand
import com.konami.ailens.ble.command.SubscribeIMUCommand
import com.konami.ailens.function.FunctionAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.collections.listOf

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
            AssetFaceLoader.loadToRepository(application, repo, recognizer, margin = 0.25f)
            _ready.emit(true)
        }

        val aiLens = BLEService.instance.lastDevice.value!!
        val session = BLEService.instance.getSession(aiLens.device.address)!!
        session.add(OpenCanvasCommand(session))
        session.add(SubscribeIMUCommand(session))
        collectBLEImu()
    }

    private fun collectBLEImu() {
        val aiLens = BLEService.instance.lastDevice.value!!
        val session = BLEService.instance.getSession(aiLens.device.address)!!

        viewModelScope.launch {
            launch {
                val accelFlow = MutableStateFlow(HeadTracker.Vector3(0.0, 0.0, 0.0))
                val magFlow = MutableStateFlow(HeadTracker.Vector3(0.0, 0.0, 0.0))

                session.imuFlow.collect { data ->
                    when (data.type) {
                        DeviceSession.IMUType.GYROS -> {
                            tracker.onGyro(data.x, data.y, data.z)
                        }

                        DeviceSession.IMUType.ACCEL -> {
                            accelFlow.value = HeadTracker.Vector3(data.x.toDouble(), data.y.toDouble(), data.z.toDouble())
                        }

                        DeviceSession.IMUType.MAGNE -> {
                            magFlow.value = HeadTracker.Vector3(data.x.toDouble(), data.y.toDouble(), data.z.toDouble())
                        }

                        DeviceSession.IMUType.ALGO -> { /* 忽略 */
                        }
                    }
                }

                launch {
                    combine(accelFlow, magFlow) { accel, mag -> accel to mag }
                        .collect { (accel, mag) ->
                            tracker.onAccelAndMag(accel, mag)
                        }
                }
            }
        }

        viewModelScope.launch {
            launch {
                combine(tracker.dxFlow, tracker.dyFlow) { x, y -> x to y }.collect { (xf, yf) ->
                    lastSentX = xf.toInt()
                    lastSentY = yf.toInt()
                }
            }
        }

    }

    fun updateFaceResult(faceResult: List<FaceAnalyzer.RecognizedFace>) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDrawMs < drawIntervalMs)
            return

        lastDrawMs = now

        this.faceResult = faceResult
        val aiLens = BLEService.instance.lastDevice.value!!
        val session = BLEService.instance.getSession(aiLens.device.address)!!

        session.add(ClearCanvasCommand(session))
        session.add(DrawRectCommand(session, 0, 0, 639, 479, 1, false))
        faceResult.forEach {
            val rect = RectF(it.box)
            val cmd = DrawRectCommand(
                session = session,
                x = (rect.left + lastSentX).toInt(),
                y = (rect.top  + lastSentY).toInt(),
                width = rect.width().toInt(),
                height = rect.height().toInt(),
                lineWidth = 1,
                fill = true
            )
            session.add(cmd)
        }
    }

    fun resetCenter() {
        tracker.recenter()
    }

    fun recalibrate() {
        tracker.restartCalibration()
    }
}