package com.konami.ailens.facedetection

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class OnnxFaceRecognizer(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        // 從 assets 載入模型（一次性）
        val modelBytes = context.assets.open("mobilefacenet.onnx").use { it.readBytes() }
        val so = OrtSession.SessionOptions().apply {
            // CPU 就好；GPU 要額外 provider
        }
        session = env.createSession(modelBytes, so)
    }

    /** 輸入 112x112 的 RGB Bitmap，輸出 embedding（128 或 512，依模型而定） */
    fun embed(face112: Bitmap): FloatArray {
        require(face112.width == 112 && face112.height == 112)

        // Bitmap -> NCHW float32 with (x - 127.5f) / 128f
        val plane = 112 * 112
        val chw = FloatArray(3 * plane)

        val pixels = IntArray(plane)
        face112.getPixels(pixels, 0, 112, 0, 0, 112, 112)
        for (y in 0 until 112) {
            for (x in 0 until 112) {
                val idx = y * 112 + x
                val p = pixels[idx]
                val r = ((p shr 16) and 0xFF).toFloat()
                val g = ((p shr 8) and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()
                val rn = (r - 127.5f) / 128f
                val gn = (g - 127.5f) / 128f
                val bn = (b - 127.5f) / 128f
                chw[idx] = rn
                chw[plane + idx] = gn
                chw[2 * plane + idx] = bn
            }
        }

        // 用 FloatBuffer 這個 overload（避免你現在遇到的型別匹配問題）
        val shape = longArrayOf(1, 3, 112, 112)
        val fb = ByteBuffer
            .allocateDirect(chw.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        fb.put(chw)
        fb.rewind()

        val inputName = session.inputNames.first()
        val tensor = OnnxTensor.createTensor(env, fb, shape)

        val results = session.run(mapOf(inputName to tensor))
        val outTensor = results[0] as OnnxTensor
        val emb = (outTensor.value as Array<FloatArray>)[0]

        l2NormalizeInPlace(emb)
        return emb
    }

    private fun l2NormalizeInPlace(v: FloatArray) {
        var sum = 0f
        for (x in v) sum += x * x
        val inv = 1f / (sqrt(sum) + 1e-10f)
        for (i in v.indices) v[i] *= inv
    }

    companion object {
        /** cosine similarity for L2-normalized vectors == dot */
        fun cosine(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            val n = minOf(a.size, b.size)
            for (i in 0 until n) s += a[i] * b[i]
            return s
        }
    }
}
