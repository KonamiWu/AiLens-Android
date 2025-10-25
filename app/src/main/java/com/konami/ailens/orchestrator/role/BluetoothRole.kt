package com.konami.ailens.orchestrator.role

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.command.SendNavigationInfoCommand
import com.konami.ailens.ble.command.StartNavigationCommand
import com.konami.ailens.ble.command.TextToAgentCommand
import com.konami.ailens.ble.command.ToggleAgentCommand
import com.konami.ailens.navigation.NavStep
import com.konami.ailens.orchestrator.capability.AgentDisplayCapability
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.DeviceEventCapability
import com.konami.ailens.orchestrator.capability.NavigationDisplayCapability
import java.time.Instant

class BluetoothRole(private val session: DeviceSession, private val deviceEventHandler: DeviceEventCapability): Role, AgentDisplayCapability, NavigationDisplayCapability {
    private var sessionId: UByte = 1u

    init {
        session.deviceEventHandler = deviceEventHandler
    }
    override fun registerCapabilities(sink: CapabilitySink) {
        sink.addAgentDisplay(this)
        sink.addNavigationDisplay(this)
    }

    override fun displayResultAnswer(result: String) {
        val command = TextToAgentCommand(sessionId++, result)
        session.add(command)
    }

    override fun displayStartAgent() {
        sessionId = 0u
        val command = ToggleAgentCommand(true)
        session.add(command)
    }

    override fun displayStopAgent() {
        val command = ToggleAgentCommand(false)
        session.add(command)
    }

    // NavigationDisplayCapability implementation
    override fun displayStartNavigation() {
        val command = StartNavigationCommand()
        session.add(command)
    }

    override fun displayEndNavigation() {
        // TODO: Implement LeaveNavigationCommand if needed
        Log.d("BluetoothRole", "displayEndNavigation")
    }

    override fun displayNavigation(step: NavStep, remainingTimeInSecond: Int) {
        val hours = remainingTimeInSecond / 3600
        val minutes = (remainingTimeInSecond % 3600) / 60
        val timeString = buildString {
            if (hours > 0) {
                append("$hours 小時 ")
            }
            if (minutes > 0) {
                append("$minutes 分鐘")
            }
        }
        // Convert Drawable to Bitmap
        // Note: Icon size is already controlled by NavigationService's custom DisplayMetrics
        val iconBitmap = step.maneuverIcon?.let { drawable ->
            drawableToBitmap(drawable)
        } ?: createDefaultBitmap()
        val command = SendNavigationInfoCommand(
            guideInfo = step.instruction,
            timeRemaining = timeString,
            flag = 0x0000_0001u,
            highlighted = "0000000000#",
            icon = iconBitmap
        )
        session.add(command)
    }

    override fun displayMap(mapView: View) {
        // Not needed for BLE transmission
    }

    override fun displayRemainingDistance(meters: Int) {
        // Could be extended to send distance updates
        Log.d("BluetoothRole", "displayRemainingDistance: $meters meters")
    }

    override fun displayRemainingTime(seconds: Int) {
        // Could be extended to send time updates
        Log.d("BluetoothRole", "displayRemainingTime: $seconds seconds")
    }

    override fun displayETA(eta: Instant) {
        // Could be extended to send ETA updates
        Log.d("BluetoothRole", "displayETA: $eta")
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        // If it's already a BitmapDrawable, return the bitmap directly
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        // Otherwise, draw the drawable onto a new bitmap
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun createDefaultBitmap(): Bitmap {
        // Create a simple default 48x48 white bitmap
        return Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
    }
}