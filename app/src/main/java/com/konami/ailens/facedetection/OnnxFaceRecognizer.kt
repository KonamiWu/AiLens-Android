package com.konami.ailens.facedetection

import ai.onnxruntime.*
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap
import org.opencv.calib3d.Calib3d

class OnnxFaceRecognizer(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets.open("mobilefacenet.onnx").use { it.readBytes() }
        val so = OrtSession.SessionOptions().apply { }
        session = env.createSession(modelBytes, so)
    }

    fun embed(face112: Bitmap): FloatArray {
        require(face112.width == 112 && face112.height == 112)

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

    fun alignFace(
        src: Bitmap,
        landmarks: List<PointF>,
        outSize: Int = 112,
        scale: Float = 1f,
        shiftX: Float = 0f,
        shiftY: Float = -6f
    ): Bitmap? {
        require(landmarks.size >= 5) { "Need 5 landmarks" }

        // 1) Bitmap -> Mat
        val srcMat = Mat()
        Utils.bitmapToMat(src, srcMat) // 允許 RGBA 也沒關係

        // 2) ArcFace 模板 + 視野調整（與你 Android 一致）
        val base = arrayOf(
            Point(38.2946, 51.6963),
            Point(73.5318, 51.5014),
            Point(56.0252, 71.7366),
            Point(41.5493, 92.3655),
            Point(70.7299, 92.2041)
        )
        val cx = outSize / 2.0
        val cy = outSize / 2.0
        val dstPoints = base.map {
            val dx = (it.x - cx) * scale + cx + shiftX
            val dy = (it.y - cy) * scale + cy + shiftY
            Point(dx, dy)
        }

        val srcPoints = landmarks.take(5).map { Point(it.x.toDouble(), it.y.toDouble()) }

        // 3) 用 MatOfPoint2f（OpenCV Java 這個型別最穩）
        val srcPts = MatOfPoint2f(*srcPoints.toTypedArray())
        val dstPts = MatOfPoint2f(*dstPoints.toTypedArray())

        // 4) 正確的 estimateAffinePartial2D 用法：它回傳 2x3 Mat
        //    第三個參數是 inliers mask，不是輸出矩陣
        val inliers = Mat()
        var M = Calib3d.estimateAffinePartial2D(
            srcPts, dstPts, inliers,
            Calib3d.LMEDS,   // 或 Calib3d.RANSAC
            3.0,             // ransacReprojThreshold（LMEDS 其實會忽略）
            2000L,           // maxIters
            0.99,            // confidence
            10L              // refineIters
        )

        // 5) 防呆：M 有可能為空或含 NaN
        val needFallback = M.empty() || M.rows() != 2 || M.cols() != 3 ||
                M.get(0,0).isEmpty() || M.get(1,1).isEmpty() ||
                M.get(0,0)[0].isNaN() || M.get(1,1)[0].isNaN()

        if (needFallback) {
            Log.e("AlignFace", "estimateAffinePartial2D returned invalid matrix, using Umeyama fallback")
            // 你的 Umeyama 相似變換（只含旋轉+等比縮放+平移）
            M = estimateSimilarity2D(srcPoints, dstPoints)
        }

        // 6)（可選）檢查仿射的尺度是否一致、是否近似正交
        runCatching {
            val a = M.get(0, 0)[0]; val b = M.get(0, 1)[0]
            val d = M.get(1, 0)[0]; val e = M.get(1, 1)[0]
            val dot = a * d + b * e
            val l0 = kotlin.math.sqrt(a * a + b * b)
            val l1 = kotlin.math.sqrt(d * d + e * e)
//            Log.e("AlignCheck", "dot=$dot, scale0=$l0, scale1=$l1")
        }

        // 7) 套用仿射
        val alignedMat = Mat()
        Imgproc.warpAffine(
            srcMat, alignedMat, M,
            Size(outSize.toDouble(), outSize.toDouble()),
            Imgproc.INTER_LINEAR, Core.BORDER_REFLECT_101
        )

        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(alignedMat, out)

        // 8) 釋放
        srcMat.release()
        alignedMat.release()
        srcPts.release()
        dstPts.release()
        inliers.release()
        M.release()

        return out
    }



    fun estimateAffine2D(
        srcPoints: List<Point>,
        dstPoints: List<Point>
    ): Mat {
        require(srcPoints.size == dstPoints.size && srcPoints.size >= 3) {
            "至少需要三對對應點"
        }

        val n = srcPoints.size
        val A = Mat.zeros(n * 2, 6, CvType.CV_64F)
        val B = Mat.zeros(n * 2, 1, CvType.CV_64F)

        for (i in 0 until n) {
            val x = srcPoints[i].x
            val y = srcPoints[i].y
            val u = dstPoints[i].x
            val v = dstPoints[i].y

            // Row 2*i
            A.put(2 * i, 0, x)
            A.put(2 * i, 1, y)
            A.put(2 * i, 2, 1.0)
            A.put(2 * i, 3, 0.0)
            A.put(2 * i, 4, 0.0)
            A.put(2 * i, 5, 0.0)
            B.put(2 * i, 0, u)

            // Row 2*i + 1
            A.put(2 * i + 1, 0, 0.0)
            A.put(2 * i + 1, 1, 0.0)
            A.put(2 * i + 1, 2, 0.0)
            A.put(2 * i + 1, 3, x)
            A.put(2 * i + 1, 4, y)
            A.put(2 * i + 1, 5, 1.0)
            B.put(2 * i + 1, 0, v)
        }

        val affineParams = Mat()
        Core.solve(A, B, affineParams, Core.DECOMP_SVD)

        val affineMat = Mat(2, 3, CvType.CV_64F)
        for (row in 0 until 2) {
            for (col in 0 until 3) {
                val value = affineParams.get(row * 3 + col, 0)[0]
                affineMat.put(row, col, value)
            }
        }

        A.release()
        B.release()
        affineParams.release()

        return affineMat
    }

    // OnnxFaceRecognizer.kt ─── 請把這兩個「輔助函式」也貼到同一個類別裡（可標 private）

    /** 以 Umeyama 方法估計 2D 相似變換 (R, s, t)，輸出 2x3 仿射矩陣（CV_64F）。 */


    /** Umeyama 2D 相似變換：輸出 2x3（CV_64F），只包含旋轉+等比縮放+平移 */
    private fun estimateSimilarity2D(src: List<Point>, dst: List<Point>): Mat {
        require(src.size == dst.size && src.size >= 2) { "Need >=2 pairs" }
        val n = src.size

        // (n x 2) double matrices
        val X = Mat(n, 2, CvType.CV_64F)
        val Y = Mat(n, 2, CvType.CV_64F)
        for (i in 0 until n) {
            X.put(i, 0, src[i].x); X.put(i, 1, src[i].y)
            Y.put(i, 0, dst[i].x); Y.put(i, 1, dst[i].y)
        }

        // means (1 x 2)
        val meanX = Mat(1, 2, CvType.CV_64F)
        val meanY = Mat(1, 2, CvType.CV_64F)
        Core.reduce(X, meanX, 0, Core.REDUCE_AVG)
        Core.reduce(Y, meanY, 0, Core.REDUCE_AVG)

        // center
        val Xc = Mat(); val Yc = Mat()
        Core.subtract(X, repeatRow(meanX, n), Xc)
        Core.subtract(Y, repeatRow(meanY, n), Yc)

        // Sigma = (Xc^T * Yc) / n
        val Xt = Xc.t()
        val Sigma = Mat(2, 2, CvType.CV_64F)
        Core.gemm(Xt, Yc, 1.0 / n, Mat(), 0.0, Sigma)

        // SVD: Sigma = U * diag(w) * V^T
        val w = Mat(); val U = Mat(); val Vt = Mat()
        Core.SVDecomp(Sigma, w, U, Vt)

        // R = U * S * V^T  (handle reflection)
        val UVt = Mat()
        Core.gemm(U, Vt, 1.0, Mat(), 0.0, UVt)
        val det = Core.determinant(UVt)
        UVt.release()

        val S = Mat.eye(2, 2, CvType.CV_64F)
        if (det < 0) S.put(1, 1, -1.0)

        val SVt = Mat()
        Core.gemm(S, Vt, 1.0, Mat(), 0.0, SVt)
        val R = Mat(2, 2, CvType.CV_64F)
        Core.gemm(U, SVt, 1.0, Mat(), 0.0, R)
        SVt.release()

        // scale s = trace(R * Sigma) / var(X)
        val RS = Mat()
        Core.gemm(R, Sigma, 1.0, Mat(), 0.0, RS)
        val trace = Core.trace(RS).`val`[0]
        RS.release()

        val l2 = Core.norm(Xc, Core.NORM_L2)
        val varX = (l2 * l2) / n
        val s = if (varX > 1e-12) trace / varX else 1.0

        // sR
        val sR = Mat()
        Core.multiply(R, Scalar.all(s), sR)

        // t = meanY^T - sR * meanX^T
        val meanXt = meanX.t()
        val sRmx = Mat()
        Core.gemm(sR, meanXt, 1.0, Mat(), 0.0, sRmx) // (2x2)*(2x1) = (2x1)
        val t = Mat()
        Core.subtract(meanY.t(), sRmx, t)            // (2x1)
        sRmx.release(); meanXt.release()

        // Assemble 2x3
        val M = Mat(2, 3, CvType.CV_64F)
        M.put(0, 0, sR.get(0,0)[0], sR.get(0,1)[0], t.get(0,0)[0])
        M.put(1, 0, sR.get(1,0)[0], sR.get(1,1)[0], t.get(1,0)[0])

        // cleanup
        listOf(X, Y, meanX, meanY, Xc, Yc, Xt, Sigma, w, U, Vt, R, sR, t).forEach { it.release() }

        return M
    }

    /** 將 1x2 Row 複製成 (n x 2)，方便做去中心化 */
    private fun repeatRow(row: Mat, times: Int): Mat {
        val out = Mat(times, row.cols(), row.type())
        for (i in 0 until times) row.copyTo(out.row(i))
        return out
    }




    companion object {
        fun cosine(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            val n = minOf(a.size, b.size)
            for (i in 0 until n) s += a[i] * b[i]
            return s
        }
    }
}