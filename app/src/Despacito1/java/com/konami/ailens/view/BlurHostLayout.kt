package com.konami.ailens.view

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.withSave
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BlurHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    // ----- Public knobs -----
    var frozen: Boolean = false
        private set

    /** If <= 0, we treat as default interval. */
    var defaultUpdateIntervalMs: Long = 80L

    // ----- Internal -----
    private val hostLoc = IntArray(2)
    private val viewLoc = IntArray(2)

    private val workerThread = HandlerThread("BlurHostWorker").apply { start() }
    private val worker = Handler(workerThread.looper)
    private val main = Handler(Looper.getMainLooper())

    private data class BucketKey(val dsQ: Int)
    private data class BlurKey(val dsQ: Int, val radiusQ: Int)

    private class Bucket {
        var captureScheduled = false
        var lastCaptureUptime = 0L
        var intervalMs = 80L
        var srcBitmap: Bitmap? = null
        var srcW = 0
        var srcH = 0
        var generation = 0
        var needsInitialInvalidate = true
        var hasCaptured = false
    }

    private data class BlurEntry(
        val generation: Int,
        val bitmap: Bitmap
    )

    private val buckets = HashMap<BucketKey, Bucket>(4)
    private val blurCache = HashMap<BlurKey, BlurEntry>(12)
    private val blurInvalidated = HashSet<BlurKey>()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        buckets.values.forEach { it.srcBitmap?.recycle() }
        blurCache.values.forEach { it.bitmap.recycle() }
        buckets.clear()
        blurCache.clear()
        blurInvalidated.clear()
        workerThread.quitSafely()
    }

    fun setFrozen(value: Boolean) {
        frozen = value
        if (!value) {
            buckets.values.forEach { it.hasCaptured = false }
            blurInvalidated.clear()
            postInvalidateOnAnimation()
        }
    }

    private fun backdropView(): View? {
        if (isInEditMode) return null
        return try {
            val id = resources.getIdentifier("blurBackdrop", "id", context.packageName)
            findViewById<View>(id)
        } catch (e: Exception) {
            null
        }
    }

    private fun foregroundView(): View? {
        if (isInEditMode) return null
        return try {
            val id = resources.getIdentifier("blurForeground", "id", context.packageName)
            findViewById<View>(id)
        } catch (e: Exception) {
            null
        }
    }

    private fun quantizeDownsample(ds: Float): Int {
        // e.g. 0.25 -> 250
        return (ds.coerceIn(0.1f, 1f) * 1000f).roundToInt()
    }

    private fun quantizeRadius(r: Float): Int {
        // quantize radius in *bitmap space* to reduce cache explosion (0.5px step)
        return (r.coerceAtLeast(0f) * 2f).roundToInt()
    }

    private fun ensureBucket(ds: Float, requestedIntervalMs: Long): Bucket {
        val dsQ = quantizeDownsample(ds)
        val key = BucketKey(dsQ)
        val b = buckets.getOrPut(key) { Bucket() }
        val interval = if (requestedIntervalMs > 0) requestedIntervalMs else defaultUpdateIntervalMs
        // If some view wants faster refresh, we honor the fastest.
        b.intervalMs = min(b.intervalMs, interval)
        return b
    }

    private fun ensureSrcBitmap(bucket: Bucket, hostW: Int, hostH: Int, ds: Float) {
        val bw = max(1, (hostW * ds).roundToInt())
        val bh = max(1, (hostH * ds).roundToInt())
        if (bucket.srcBitmap == null || bucket.srcW != bw || bucket.srcH != bh) {
            bucket.srcBitmap?.recycle()
            bucket.srcBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            bucket.srcW = bw
            bucket.srcH = bh
            bucket.generation++
        }
    }

    private fun requestCapture(bucket: Bucket, ds: Float) {
        if (frozen) {
            return
        }
        if (bucket.hasCaptured) {
            return
        }
        if (bucket.captureScheduled) {
            return
        }

        android.util.Log.e("BlurHostLayout", "requestCapture: scheduling first capture")
        bucket.captureScheduled = true
        post {
            bucket.captureScheduled = false
            if (frozen) return@post

            val hostW = width
            val hostH = height
            if (hostW <= 0 || hostH <= 0) return@post

            val backdrop = backdropView() ?: return@post

            ensureSrcBitmap(bucket, hostW, hostH, ds)
            val src = bucket.srcBitmap ?: return@post

            val c = Canvas(src)
            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            c.withSave {
                c.scale(ds, ds)
                backdrop.draw(c)
            }

            bucket.lastCaptureUptime = SystemClock.uptimeMillis()
            bucket.generation++
            bucket.hasCaptured = true

            if (bucket.needsInitialInvalidate) {
                bucket.needsInitialInvalidate = false
                invalidateAllChildren()
            }
        }
    }

    private fun invalidateAllChildren() {
        fun invalidateRecursive(view: View) {
            view.postInvalidate()
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    invalidateRecursive(view.getChildAt(i))
                }
            }
        }
        invalidateRecursive(this)
    }

    private fun getBlurredAsync(
        bucket: Bucket,
        ds: Float,
        radiusPx: Float,
        onReady: (BlurEntry?) -> Unit
    ) {
        val src = bucket.srcBitmap
        if (src == null) {
            onReady(null)
            return
        }

        val radiusInBitmap = radiusPx * ds
        val rQ = quantizeRadius(radiusInBitmap)
        val dsQ = quantizeDownsample(ds)

        val key = BlurKey(dsQ, rQ)
        val cached = blurCache[key]
        if (cached != null) {
            onReady(cached)
            return
        }

        val alreadyInvalidated = synchronized(blurInvalidated) {
            blurInvalidated.contains(key)
        }

        if (alreadyInvalidated) {
            onReady(null)
            return
        }

        onReady(null)

        worker.post {
            val out = src.copy(Bitmap.Config.ARGB_8888, true)
            val rInt = max(0, radiusInBitmap.roundToInt())
            if (rInt > 0) {
                StackBlur.blurInPlace(out, rInt)
            }

            val entry = BlurEntry(bucket.generation, out)
            synchronized(blurCache) {
                blurCache[key]?.bitmap?.recycle()
                blurCache[key] = entry

                if (blurCache.size > 12) {
                    val it = blurCache.entries.iterator()
                    if (it.hasNext()) {
                        val victim = it.next()
                        victim.value.bitmap.recycle()
                        it.remove()
                    }
                }
            }

            val shouldInvalidate = synchronized(blurInvalidated) {
                if (!blurInvalidated.contains(key)) {
                    blurInvalidated.add(key)
                    true
                } else {
                    false
                }
            }

            if (shouldInvalidate) {
                main.post {
                    invalidateAllChildren()
                }
            }
        }
    }

    /**
     * Draw blurred backdrop behind a target view.
     * The caller (BlurBorderView) provides clipPath in its own local coordinates.
     */
    fun drawBlurBehind(
        targetView: View,
        canvas: Canvas,
        clipPath: Path,
        radiusPx: Float,
        downsample: Float,
        updateIntervalMs: Long,
        paint: Paint,
        srcRectReuse: Rect,
        dstRectReuse: Rect
    ) {
        val ds = downsample.coerceIn(0.1f, 1f)
        val bucket = ensureBucket(ds, updateIntervalMs)
        requestCapture(bucket, ds)

        getLocationInWindow(hostLoc)
        targetView.getLocationInWindow(viewLoc)

        val leftInHost = (viewLoc[0] - hostLoc[0]).toFloat()
        val topInHost = (viewLoc[1] - hostLoc[1]).toFloat()

        val vw = targetView.width
        val vh = targetView.height
        if (vw <= 0 || vh <= 0) return

        getBlurredAsync(bucket, ds, radiusPx) { entry ->
            val bmp = entry?.bitmap ?: return@getBlurredAsync

            val l = (leftInHost * ds).roundToInt()
            val t = (topInHost * ds).roundToInt()
            val r = ((leftInHost + vw) * ds).roundToInt()
            val b = ((topInHost + vh) * ds).roundToInt()

            val cl = l.coerceIn(0, bmp.width)
            val ct = t.coerceIn(0, bmp.height)
            val cr = r.coerceIn(0, bmp.width)
            val cb = b.coerceIn(0, bmp.height)
            if (cr <= cl || cb <= ct) return@getBlurredAsync

            srcRectReuse.set(cl, ct, cr, cb)
            dstRectReuse.set(0, 0, vw, vh)

            canvas.withSave {
                canvas.clipPath(clipPath)
                canvas.drawBitmap(bmp, srcRectReuse, dstRectReuse, paint)
            }

            if (!frozen) targetView.postInvalidateOnAnimation()
        }
    }
}
