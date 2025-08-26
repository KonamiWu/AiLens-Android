package com.konami.ailens.facedetection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.system.measureNanoTime

class FaceAnalyzer(
    private val previewView: PreviewView,
    private val overlayView: FaceOverlayView,
    private val appContext: Context,
    private val repo: FaceRepository,
    private val useFrontCamera: Boolean = true
) : ImageAnalysis.Analyzer {

    data class RecognizedFace(
        val box: Rect,
        val name: String,
        val face112: Bitmap
    )

    data class Sample(
        val image: Bitmap,
        val faceBoxes: List<Rect>,
        val rotation: Int
    )

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    private val recognizer by lazy { OnnxFaceRecognizer(appContext) }
    private var frameId = 0

    private val frameFlow = MutableSharedFlow<Sample?>(extraBufferCapacity = 1)

    private val _faceResultsFlow = MutableSharedFlow<List<RecognizedFace>>()
    val faceResultsFlow = _faceResultsFlow.asSharedFlow()

    private val minScore = 0.6f

    init {
        CoroutineScope(Dispatchers.Default).launch {
            frameFlow.collect { sample ->
                if (sample != null) {
                    val results = mutableListOf<RecognizedFace>()
                    sample.faceBoxes.forEach { faceBox ->
                        try {
                            val correctedImage = sample.image.rotateAndMirror(sample.rotation, useFrontCamera)
                            val finalBox = if (useFrontCamera) {
                                val mirroredLeft = correctedImage.width - faceBox.right
                                val mirroredRight = correctedImage.width - faceBox.left
                                Rect(mirroredLeft, faceBox.top, mirroredRight, faceBox.bottom)
                            } else {
                                faceBox
                            }
                            finalBox.inset(-(finalBox.width() * 0.1).toInt(), -(finalBox.height() * 0.1).toInt())
                            val crop = Bitmap.createBitmap(
                                correctedImage,
                                finalBox.left.coerceAtLeast(0),
                                finalBox.top.coerceAtLeast(0),
                                finalBox.width().coerceAtMost(correctedImage.width - finalBox.left),
                                finalBox.height().coerceAtMost(correctedImage.height - finalBox.top)
                            ).toFace112()

                            val emb = recognizer.embed(crop)
                            val timeNs = measureNanoTime {
                                val name = repo.findBest(emb, minScore = minScore)
                                if (name != null) {
                                    results.add(RecognizedFace(faceBox, name, crop))
                                } else {
                                    results.add(RecognizedFace(faceBox, "Unknown", crop))
                                }
                            }

                        } catch (t: Throwable) {
                            Log.e("FaceAnalyzer", "recognize failed", t)
                        }
                    }
                    _faceResultsFlow.emit(results)
                } else {
                    _faceResultsFlow.emit(listOf())
                }
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        overlayView.setTransformInfo(
            imageWidth = inputImage.width,
            imageHeight = inputImage.height,
            viewWidth = previewView.width,
            viewHeight = previewView.height,
            rotationDegrees = rotation,
            isFrontCamera = useFrontCamera
        )

        detector.process(inputImage).addOnSuccessListener { faces ->
            if (++frameId % 5 == 0) {
                if (faces.isNotEmpty()) {
                    try {
                        val bitmap = imageProxy.toBitmap()
                        val boxes = faces.map { it.boundingBox }
                        frameFlow.tryEmit(Sample(bitmap, boxes, rotation))
                    } catch (e: Exception) {
                        Log.e("FaceAnalyzer", "bitmap conversion failed", e)
                    }
                } else {
                    frameFlow.tryEmit(null)
                }
            }
        }.addOnFailureListener { e ->
            Log.e("FaceAnalyzer", "Face detect failed", e)
        }.addOnCompleteListener {
            imageProxy.close()
        }
    }
}
