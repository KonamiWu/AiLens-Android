package com.konami.ailens.ble

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * HeadTracker：輸出頭部造成的位移量（dx, dy），單位像素。
 * dx/dy 是以螢幕中心為原點的偏移量：
 *   dx > 0 表示往右偏、dy > 0 表示往下偏（可用 ySign 控制上下方向）。
 * 你要讓矩形跟頭部「反方向」移動：newX = baseX - dx, newY = baseY - dy。
 */
class HeadTracker(
    private val screenWidth: Int = 640,
    private val screenHeight: Int = 480,
    private val hfovDeg: Double = 30.0,
    private val vfovDeg: Double = 30.0,

    // 想要「頭往上 → dy 為正（畫面向下）」就用 +1.0；反過來用 -1.0
    private val ySign: Double = +1.0,

    // 是否使用磁力計參與 yaw 修正（室內干擾大可關掉）
    private val useMag: Boolean = true,

    // EMA 平滑係數（0~1，越大越平滑；=0 表示不平滑）
    private val ema: Double = 0.2,

    // 校準參數
    private val stillnessThreshold: Double = 0.03, // rad/s
    private val calSamplesTarget: Int = 500,
    private val minCalTimeMs: Long = 2000L,

    // 磁力計權重上限（每次融合）
    private val magSlerpMax: Double = 0.02
) {
    // --- 位移輸出（像素） ---
    private val _dxFlow = MutableStateFlow(0f)
    val dxFlow: StateFlow<Float> get() = _dxFlow
    private val _dyFlow = MutableStateFlow(0f)
    val dyFlow: StateFlow<Float> get() = _dyFlow

    // --- 姿態狀態 ---
    private var quaternion = Quaternion()           // 目前姿態（Head -> World）
    private var referenceQuaternion = Quaternion()  // recenter 參考

    private var lastNanos = 0L

    // 陀螺儀偏置校準
    private var calibrating = true
    private var calSum = Vector3(0.0, 0.0, 0.0)
    private var calCount = 0
    private var gyroBias = Vector3(0.0, 0.0, 0.0)
    private var calStartMs = SystemClock.uptimeMillis()

    // 透視投影（焦距，像素）
    private val hfovRad = Math.toRadians(hfovDeg)
    private val vfovRad = Math.toRadians(vfovDeg)
    private val fx = screenWidth  / (2.0 * tan(hfovRad / 2.0))
    private val fy = screenHeight / (2.0 * tan(vfovRad / 2.0))

    // EMA 平滑暫存（針對位移量）
    private var emaDx: Double = 0.0
    private var emaDy: Double = 0.0

    // --- 安裝矩陣：Body(裝置) → Head(右+X、上+Y、前- Z) ---
    // 這組是你目前實機可用的對應：Xb -> Xh, Yb -> Zh, Zb -> Yh
    private var R_BH = arrayOf(
        doubleArrayOf( 1.0,  0.0,  0.0),
        doubleArrayOf( 0.0,  0.0,  1.0),
        doubleArrayOf( 0.0,  1.0,  0.0)
    )
    fun setMountingMatrix(m: Array<DoubleArray>) {
        require(m.size == 3 && m.all { it.size == 3 }) { "R_BH must be 3x3" }
        R_BH = arrayOf(m[0].clone(), m[1].clone(), m[2].clone())
    }
    private fun mapBodyToHead(v: Vector3): Vector3 {
        val r = R_BH
        return Vector3(
            r[0][0]*v.x + r[0][1]*v.y + r[0][2]*v.z,
            r[1][0]*v.x + r[1][1]*v.y + r[1][2]*v.z,
            r[2][0]*v.x + r[2][1]*v.y + r[2][2]*v.z
        )
    }

    // --- 感測輸入：陀螺儀（rad/s） ---
    fun onGyro(gx: Float, gy: Float, gz: Float) {
        val now = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastNanos == 0L) 0.0 else (now - lastNanos) / 1e9
        lastNanos = now

        val gHead = mapBodyToHead(Vector3(gx.toDouble(), gy.toDouble(), gz.toDouble()))
        val gyroMagnitude = gHead.magnitude()

        if (gyroMagnitude < stillnessThreshold && calibrating) {
            val nowMs = SystemClock.uptimeMillis()
            calSum += gHead
            calCount++
            if (calCount >= calSamplesTarget && (nowMs - calStartMs) >= minCalTimeMs) {
                gyroBias = calSum / calCount.toDouble()
                calibrating = false
                Log.d("HeadTracker", "Calibration complete: gyroBias=$gyroBias")
            }
        }

        val corrected = gHead - gyroBias
        if (dt > 0.0) {
            val dq = Quaternion.fromRotationVector(corrected, dt)
            quaternion = quaternion.multiply(dq).normalize()
            publishDelta()
        }
    }

    // --- 感測輸入：加速度計 + 磁力計 ---
    fun onAccelAndMag(accelBody: Vector3, magBody: Vector3) {
        val accel = mapBodyToHead(accelBody)
        val mag = mapBodyToHead(magBody)

        // 只校正 roll/pitch（不動 yaw）
        tiltCorrectWithAccel(accel, strength = 0.02)

        if (useMag) {
            val accMagQ = getAccMagQuaternion(accel, mag)
            quaternion = quaternion.slerp(accMagQ, magSlerpMax).normalize()
        }
        publishDelta()
    }

    // 傾斜校正（roll/pitch）
    private fun tiltCorrectWithAccel(accel: Vector3, strength: Double) {
        val g = accel.normalize()
        if (g.magnitude() < 1e-6) return

        val upHead = Vector3(0.0, 1.0, 0.0)
        val upCur = quaternion.rotate(upHead)
        val upMeas = (-g).normalize()

        val dot = (upCur.x*upMeas.x + upCur.y*upMeas.y + upCur.z*upMeas.z).coerceIn(-1.0, 1.0)
        val angle = acos(dot)
        if (angle < 1e-4) return

        val axis = upCur.cross(upMeas).normalize()
        val corrAngle = angle * strength.coerceIn(0.0, 1.0)
        val qCorr = Quaternion.fromAxisAngle(axis, corrAngle)
        quaternion = qCorr.multiply(quaternion).normalize()
    }

    // 從加速度+磁力計建姿態（供 yaw 修正）
    private fun getAccMagQuaternion(accel: Vector3, mag: Vector3): Quaternion {
        val g = accel.normalize()
        if (g.magnitude() < 1e-6) return quaternion

        val m = mag.normalize()
        var east = m.cross(g).normalize()
        if (east.magnitude() < 1e-6) east = Vector3(1.0, 0.0, 0.0)
        val north = g.cross(east).normalize()

        val R = doubleArrayOf(
            east.x,  north.x,  g.x,
            east.y,  north.y,  g.y,
            east.z,  north.z,  g.z
        )
        return Quaternion.fromMatrix(R).normalize()
    }

    // 置中：以當前姿態為 0，重置位移平滑
    fun recenter() {
        referenceQuaternion = quaternion.inverse()
        emaDx = 0.0
        emaDy = 0.0
        publishDelta()
    }

    fun restartCalibration() {
        calibrating = true
        calSum = Vector3(0.0, 0.0, 0.0)
        calCount = 0
        gyroBias = Vector3(0.0, 0.0, 0.0)
        calStartMs = SystemClock.uptimeMillis()
    }

    // --- 將目前姿態轉成位移量（dx, dy）---
    private fun publishDelta() {
        val qRel = referenceQuaternion.multiply(quaternion).normalize()

        // 直接用 yaw / pitch，再用 tan 轉成像素位移
        val yaw   = qRel.getYaw()
        val pitch = qRel.getPitch()

        var dx = fx * tan(yaw)
        var dy = fy * tan(pitch) * ySign

        // EMA 平滑（針對位移）
        if (ema > 0.0) {
            emaDx = emaDx * (1.0 - ema) + dx * ema
            emaDy = emaDy * (1.0 - ema) + dy * ema
            dx = emaDx
            dy = emaDy
        }

        _dxFlow.value = dx.toFloat()
        _dyFlow.value = dy.toFloat()
    }

    // =============== 數學工具 ===============
    data class Vector3(val x: Double, val y: Double, val z: Double) {
        fun magnitude(): Double = sqrt(x * x + y * y + z * z)
        fun normalize(): Vector3 {
            val m = magnitude()
            return if (m < 1e-12) Vector3(0.0, 0.0, 0.0) else Vector3(x/m, y/m, z/m)
        }
        operator fun plus(o: Vector3) = Vector3(x+o.x, y+o.y, z+o.z)
        operator fun minus(o: Vector3) = Vector3(x-o.x, y-o.y, z-o.z)
        operator fun times(s: Double) = Vector3(x*s, y*s, z*s)
        operator fun div(s: Double) = Vector3(x/s, y/s, z/s)
        fun cross(o: Vector3) = Vector3(
            y*o.z - z*o.y,
            z*o.x - x*o.z,
            x*o.y - y*o.x
        )
        operator fun unaryMinus() = Vector3(-x, -y, -z)
    }

    data class Quaternion(var w: Double = 1.0, var x: Double = 0.0, var y: Double = 0.0, var z: Double = 0.0) {
        companion object {
            fun fromRotationVector(v: Vector3, dt: Double): Quaternion {
                val angle = v.magnitude() * dt
                if (angle < 1e-12) return Quaternion()
                val axis = v.normalize()
                val s = sin(angle / 2.0)
                val c = cos(angle / 2.0)
                return Quaternion(c, axis.x*s, axis.y*s, axis.z*s)
            }
            fun fromAxisAngle(axis: Vector3, angle: Double): Quaternion {
                if (angle < 1e-12) return Quaternion()
                val a = axis.normalize()
                val s = sin(angle / 2.0)
                val c = cos(angle / 2.0)
                return Quaternion(c, a.x*s, a.y*s, a.z*s)
            }
            fun fromMatrix(m: DoubleArray): Quaternion {
                val trace = m[0] + m[4] + m[8]
                return if (trace > 0) {
                    val s = 0.5 / sqrt(trace + 1.0)
                    Quaternion(
                        w = 0.25 / s,
                        x = (m[5] - m[7]) * s,
                        y = (m[6] - m[2]) * s,
                        z = (m[1] - m[3]) * s
                    )
                } else if (m[0] > m[4] && m[0] > m[8]) {
                    val s = 2.0 * sqrt(1.0 + m[0] - m[4] - m[8])
                    Quaternion(
                        w = (m[5] - m[7]) / s,
                        x = 0.25 * s,
                        y = (m[1] + m[3]) / s,
                        z = (m[6] + m[2]) / s
                    )
                } else if (m[4] > m[8]) {
                    val s = 2.0 * sqrt(1.0 + m[4] - m[0] - m[8])
                    Quaternion(
                        w = (m[6] - m[2]) / s,
                        x = (m[1] + m[3]) / s,
                        y = 0.25 * s,
                        z = (m[5] + m[7]) / s
                    )
                } else {
                    val s = 2.0 * sqrt(1.0 + m[8] - m[0] - m[4])
                    Quaternion(
                        w = (m[1] - m[3]) / s,
                        x = (m[6] + m[2]) / s,
                        y = (m[5] + m[7]) / s,
                        z = 0.25 * s
                    )
                }
            }
        }
        fun multiply(o: Quaternion) = Quaternion(
            w = w*o.w - x*o.x - y*o.y - z*o.z,
            x = w*o.x + x*o.w + y*o.z - z*o.y,
            y = w*o.y - x*o.z + y*o.w + z*o.x,
            z = w*o.z + x*o.y - y*o.x + z*o.w
        )
        fun inverse(): Quaternion {
            val n = w*w + x*x + y*y + z*z
            return Quaternion(w/n, -x/n, -y/n, -z/n)
        }
        fun normalize(): Quaternion {
            val n = sqrt(w * w + x * x + y * y + z * z)
            if (n < 1e-12) return Quaternion()
            return Quaternion(w/n, x/n, y/n, z/n)
        }
        fun slerp(other: Quaternion, t: Double): Quaternion {
            var dot = w*other.w + x*other.x + y*other.y + z*other.z
            var a = other
            if (dot < 0) { dot = -dot; a = Quaternion(-other.w, -other.x, -other.y, -other.z) }
            if (dot > 0.9995) {
                return Quaternion(
                    w + t*(a.w - w),
                    x + t*(a.x - x),
                    y + t*(a.y - y),
                    z + t*(a.z - z)
                ).normalize()
            }
            val theta = acos(dot)
            val s = sin(theta)
            val w1 = sin((1.0 - t) * theta) / s
            val w2 = sin(t * theta) / s
            return Quaternion(
                w*w1 + a.w*w2,
                x*w1 + a.x*w2,
                y*w1 + a.y*w2,
                z*w1 + a.z*w2
            )
        }
        fun rotate(v: Vector3): Vector3 {
            val qv = Quaternion(0.0, v.x, v.y, v.z)
            val r = this.multiply(qv).multiply(this.inverse())
            return Vector3(r.x, r.y, r.z)
        }
        fun getYaw(): Double {
            val siny = 2.0 * (w * z + x * y)
            val cosy = 1.0 - 2.0 * (y * y + z * z)
            return atan2(siny, cosy)
        }
        fun getPitch(): Double {
            val sinp = 2.0 * (w * y - z * x)
            return if (abs(sinp) >= 1) {
                if (sinp > 0) Math.PI / 2 else -Math.PI / 2
            } else asin(sinp)
        }
    }
}