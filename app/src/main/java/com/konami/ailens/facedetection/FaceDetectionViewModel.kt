package com.konami.ailens.facedetection

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.command.ClearCanvasCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import androidx.core.graphics.createBitmap
import com.konami.ailens.ble.command.CloseCanvasCommand
import com.konami.ailens.ble.command.DrawTextCommand
import com.konami.ailens.ble.command.OpenCanvasCommand
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

data class RecognizedFace(
    val imageWidth: Int,
    val imageHeight: Int,
    val box: RectF,
    val name: String,
    val face112: Bitmap
)

data class DebugInfo(
    val image: Bitmap
)

class FaceDetectionViewModel(application: Application) : AndroidViewModel(application), ImageAnalysis.Analyzer {

    // Stored people embeddings
    private data class Person(val name: String, val embs: List<FloatArray>)
    private val people = mutableListOf<Person>()

    private var lastDrawMs = 0L
    private val drawIntervalMs = 200L
    private var faceResult = listOf<RecognizedFace>()

    private val recognizer by lazy { OnnxFaceRecognizer(application) }
    private val yuNet by lazy { YuNet(application) }
    
    private var count = 0

    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    private val _ready = MutableStateFlow(false)
    val readyFlow = _ready.asSharedFlow()

    private val _faceFlow = MutableSharedFlow<List<RecognizedFace>>(extraBufferCapacity = 5)
    val faceFlow = _faceFlow.asSharedFlow()

    private val _debugFlow = MutableSharedFlow<DebugInfo>(extraBufferCapacity = 5)
    val debugFlow = _debugFlow.asSharedFlow()

    private val frames = Channel<ImageProxy>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        // Ensure OpenCV is initialized before use (your OpenCVLoader.initLocal() is inside YuNet)
        OpenCVLoader.initLocal()
//        viewModelScope.launch(Dispatchers.IO) {
//            loadEmbeddingsFromAssets(application)
//            _ready.emit(true)
//        }

        viewModelScope.launch(Dispatchers.Default) {
            for (image in frames) {
                try { processFrame(image) } finally { image.close() }
            }
        }

        openCanvas()
    }

    override fun analyze(imageProxy: ImageProxy) {
        count ++
        if (count % 10 != 0) {
            imageProxy.close()
            return
        }
        if (!frames.trySend(imageProxy).isSuccess) {
            imageProxy.close()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        // --- Read YUV_420_888 planes safely ---
        val width = mediaImage.width
        val height = mediaImage.height
        val yPlane = mediaImage.planes[0]
        val uPlane = mediaImage.planes[1]
        val vPlane = mediaImage.planes[2]

        yPlane.buffer.rewind()
        uPlane.buffer.rewind()
        vPlane.buffer.rewind()

        // Assemble NV21: YYYY... VU VU ...
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21Bytes = ByteArray(ySize + uvSize)

        // Copy Y plane row by row (handle rowStride)
        var dst = 0
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yRow = ByteArray(yRowStride)
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(yRow, 0, yRowStride.coerceAtMost(yBuffer.remaining()))
            System.arraycopy(yRow, 0, nv21Bytes, dst, width)
            dst += width
        }

        // Interleave V and U into NV21
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val halfWidth = width / 2
        val halfHeight = height / 2

        for (row in 0 until halfHeight) {
            for (col in 0 until halfWidth) {
                val uIndex = row * uRowStride + col * uPixelStride
                val vIndex = row * vRowStride + col * vPixelStride
                val u = uBuffer.get(uIndex)
                val v = vBuffer.get(vIndex)
                nv21Bytes[dst++] = v
                nv21Bytes[dst++] = u
            }
        }

        // --- Convert NV21 -> BGR Mat ---
        val yuvMat = Mat(height + halfHeight, width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21Bytes)

        val bgrMat = Mat()
        Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_NV21)
        yuvMat.release()

        // --- Rotate using Core.rotate (CameraX rotationDegrees is CLOCKWISE) ---
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rotatedMat = Mat()
        when (rotationDegrees) {
            90  -> Core.rotate(bgrMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(bgrMat, rotatedMat, Core.ROTATE_180)
            270 -> Core.rotate(bgrMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> bgrMat.copyTo(rotatedMat)
        }
        bgrMat.release()

        // --- Mirror for front camera (after rotation) ---
        val isFrontCamera = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
        val finalMat = if (isFrontCamera) {
            val mirrored = Mat()
            Core.flip(rotatedMat, mirrored, 1)
            rotatedMat.release()
            mirrored
        } else {
            rotatedMat
        }

        val detections = yuNet.predict(finalMat)

        val frameBitmap = createBitmap(finalMat.width(), finalMat.height())
        org.opencv.android.Utils.matToBitmap(finalMat, frameBitmap)

        val recognized = mutableListOf<RecognizedFace>()
        for (detection in detections) {
            try {
                val aligned = recognizer.alignFace(frameBitmap, detection.landmarks)
                if (aligned == null) continue
                _debugFlow.tryEmit(DebugInfo(aligned))
                val embA = recognizer.embed(aligned)
                val matrix = android.graphics.Matrix().apply { postScale(-1f, 1f, 56f, 56f) } // 以中心翻轉
                val alignedFlip = Bitmap.createBitmap(aligned, 0, 0, 112, 112, matrix, false)
                val embB = recognizer.embed(alignedFlip)
                val embCombined = FloatArray(embA.size) { i -> embA[i] + embB[i] }
                run {
                    var s = 0f
                    for (x in embCombined) s += x * x
                    val inv = 1f / (kotlin.math.sqrt(s) + 1e-10f)
                    for (i in embCombined.indices) embCombined[i] *= inv
                }

                val name = findBest(embCombined, minScore = 0.62f) ?: "unknown"


                recognized.add(RecognizedFace(finalMat.width(), finalMat.height(), detection.box, name, aligned))
            } catch (t: Throwable) {
                Log.e("FaceVM", "Recognition failed", t)
            }
        }
        sendToGlass(recognized)
        _faceFlow.tryEmit(recognized)

        finalMat.release()
        imageProxy.close()
    }

    private fun openCanvas() {
        val aiLens = BLEService.instance.connectedSession.value ?: return
        val session = BLEService.instance.getSession(aiLens.device.address) ?: return

        session.add(OpenCanvasCommand())
    }

    private fun sendToGlass(results: List<RecognizedFace>) {
        val aiLens = BLEService.instance.connectedSession.value ?: return
        val session = BLEService.instance.getSession(aiLens.device.address) ?: return

        session.add(ClearCanvasCommand())

        if (results.isEmpty())
            return

        var unknownCount = 0
        var resultString = ""
        results.forEach {
            if (it.name == "unknown")
                unknownCount ++
            else
                resultString += (it.name + "\n")
        }
        if (unknownCount != 0)
            resultString += ("Unknown %d".format(unknownCount))
//        Log.e("TAG", "resultString = ${resultString}")
        session.add(DrawTextCommand(resultString, 0, 0, 100, 100))
    }

    private fun closeCanvas() {
        val aiLens = BLEService.instance.connectedSession.value ?: return
        val session = BLEService.instance.getSession(aiLens.device.address) ?: return

        session.add(CloseCanvasCommand())
    }

//    private fun loadEmbeddingsFromAssets(context: Context) {
//        val am = context.assets
//        am.open("embedding/people_avg.json").use { input ->
//            val json = input.readBytes().toString(Charsets.UTF_8)
//            val obj = org.json.JSONObject(json)
//            val names = obj.keys()
//            while (names.hasNext()) {
//                val name = names.next()
//                val info = obj.getJSONObject(name)
//                val list = mutableListOf<FloatArray>()
//                if (info.has("embeddings")) {
//                    val arrs = info.getJSONArray("embeddings")
//                    for (i in 0 until arrs.length()) {
//                        val a = arrs.getJSONArray(i)
//                        val emb = FloatArray(a.length()) { j -> a.getDouble(j).toFloat() }
//                        list.add(emb)
//                    }
//                } else {
//                    val a = info.getJSONArray("embedding")
//                    val emb = FloatArray(a.length()) { j -> a.getDouble(j).toFloat() }
//                    list.add(emb)
//                }
//                people.add(Person(name, list))
//            }
//        }
//    }

    /** Return best-match name or null if below threshold. */
    private fun findBest(query: FloatArray, minScore: Float): String? {
        var bestName: String? = null
        var best = -1f
        for (p in people) {
            var bestOfThisPerson = -1f
            for (e in p.embs) {
                val s = OnnxFaceRecognizer.cosine(query, e)
                if (s > bestOfThisPerson) bestOfThisPerson = s
            }
            if (bestOfThisPerson > best) {
                best = bestOfThisPerson
                bestName = p.name
            }
        }
        return if (best >= minScore) bestName else null
    }

    override fun onCleared() {
        super.onCleared()
        closeCanvas()
    }
}
