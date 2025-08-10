package com.konami.ailens.facedetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN

class YuNet(context: Context) {

    data class YuNetFace(
        val box: RectF,
        val landmarks: List<PointF>,
        val score: Float
    )

    private val faceDetector: FaceDetectorYN

    init {
        if (!OpenCVLoader.initLocal()) {
            Log.e("YuNet", "OpenCV initialization failed.")
        }

        // 將 onnx 從 assets 複製到可讀檔案路徑
        val modelFile = "face_detection_yunet_2023mar.onnx"
        val modelPath = context.filesDir.resolve(modelFile).absolutePath
        context.assets.open(modelFile).use { input ->
            context.openFileOutput(modelFile, Context.MODE_PRIVATE).use { output ->
                input.copyTo(output)
            }
        }

        // 先用一個 placeholder 的 inputSize，真正大小在 predict() 每幀 setInputSize
        faceDetector = FaceDetectorYN.create(modelPath, "", Size(320.0, 320.0))

        // ⚠️ 關鍵：降低門檻，跟你 Python 一致或更寬鬆一點
        faceDetector.scoreThreshold = 0.7f // 0.6~0.7 之間可自行微調
        faceDetector.nmsThreshold = 0.3f
        faceDetector.topK = 500
    }

    /**
     * 傳入的 frameBgr 必須已經：
     * 1) 依照 CameraX rotationDegrees 正確旋轉（建議 Core.rotate）
     * 2) 若是前鏡頭，已水平翻轉（Core.flip(..., 1)）
     * 3) 顏色空間為 BGR、型別為 CV_8UC3
     */
    fun predict(frameBgr: Mat): List<YuNetFace> {
        val input = ensureBgr8UC3(frameBgr)

        faceDetector.inputSize = Size(input.width().toDouble(), input.height().toDouble())

        val faces = Mat()
        faceDetector.detect(input, faces)

        if (input !== frameBgr) input.release()

        val results = mutableListOf<YuNetFace>()
        if (faces.empty()) {
            faces.release()
            return results
        }

        // 🔧 這裡改成 FloatArray，因為輸出是 CV_32F（type=5）
        val row = FloatArray(15)
        val W = frameBgr.width().toFloat()
        val H = frameBgr.height().toFloat()

        for (i in 0 until faces.rows()) {
            faces.get(i, 0, row)  // 一次取完該列（x,y,w,h, 5對landmarks, score）

            val x = row[0]
            val y = row[1]
            val w = row[2]
            val h = row[3]
            val score = row[14]

            val left   = x.coerceIn(0f, W - 1f)
            val top    = y.coerceIn(0f, H - 1f)
            val right  = (x + w).coerceIn(left + 1f, W)
            val bottom = (y + h).coerceIn(top + 1f, H)

            val lm = ArrayList<PointF>(5)
            var idx = 4
            repeat(5) {
                val lx = row[idx++].coerceIn(0f, W - 1f)
                val ly = row[idx++].coerceIn(0f, H - 1f)
                lm.add(PointF(lx, ly))
            }

            results.add(
                YuNetFace(
                    RectF(left, top, right, bottom),
                    lm,
                    score
                )
            )
        }

        faces.release()
        return results
    }

    private fun ensureBgr8UC3(src: Mat): Mat {
        // CV_8UC3 (BGR) 就直接回傳
        if (src.type() == CvType.CV_8UC3) return src

        val dst = Mat()
        when {
            src.type() == CvType.CV_8UC4 -> Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGBA2BGR)
            src.channels() == 3          -> Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGB2BGR)
            else                         -> throw IllegalArgumentException("Unsupported Mat type for YuNet: type=${src.type()}, channels=${src.channels()}")
        }
        return dst
    }
}
