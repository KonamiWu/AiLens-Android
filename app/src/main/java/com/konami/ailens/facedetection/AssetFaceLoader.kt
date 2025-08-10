package com.konami.ailens.facedetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

object AssetFaceLoader {
    var bitmap: Bitmap? = null

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
    }

    /** 從 assets/faces 讀圖 → 自動旋轉 → 偵測臉 → 裁切 + resize → embed → 存 repo */
    fun loadToRepository(context: Context, repo: FaceRepository, recognizer: OnnxFaceRecognizer, margin: Float = 0.25f) {
        val am = context.assets
        val dir = "faces"
        val files = am.list(dir)?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) }.orEmpty()
        if (files.isEmpty()) {
            Log.e("AssetFaceLoader", "No files under assets/$dir")
            return
        }
        Log.e("AssetFaceLoader", "Loading ${files.size} faces from assets/$dir")

        files.forEach { filename ->
            try {
                // 讀取 bitmap + exif 自動旋轉
                val src = loadBitmapFromAssetsWithExif(context, "$dir/$filename") ?: return@forEach

                val name = filename.substringBefore('_').substringBeforeLast('.') // konami_1.jpg → konami

                // 偵測最大臉（同步）
                val task = detector.process(InputImage.fromBitmap(src, 0))
                val faces = Tasks.await(task)
                val box: Rect? = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }?.boundingBox

                val face = if (box != null) src.cropWithMargin(box, margin) else src

                val face112 = Bitmap.createScaledBitmap(face, 112, 112, true)
                bitmap = face112

                val emb = recognizer.embed(face112)
                repo.add(Person(name, emb))

                Log.e("AssetFaceLoader", "Enrolled $name (${emb.size}d) from $filename")
            } catch (t: Throwable) {
                Log.e("AssetFaceLoader", "Failed on $filename", t)
            }
        }
    }

    /** 自動根據 EXIF 修正角度 */
    private fun loadBitmapFromAssetsWithExif(context: Context, path: String): Bitmap? {
        val am = context.assets

        // 1) decode 圖片
        val inputStream1 = am.open(path)
        val bitmap = BitmapFactory.decodeStream(inputStream1)
        inputStream1.close()
        if (bitmap == null) {
            Log.e("AssetFaceLoader", "Failed to decode $path")
            return null
        }

        // 2) 讀取 EXIF
        val inputStream2 = am.open(path)
        val exif = ExifInterface(inputStream2)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        inputStream2.close()

        // 3) 根據方向旋轉 bitmap
        val rotated = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> bitmap.rotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> bitmap.rotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> bitmap.rotate(270f)
            else -> bitmap
        }

        return rotated
    }

    /** Bitmap.rotate 擴充函式 */
    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
