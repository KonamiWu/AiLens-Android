package com.konami.ailens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import kotlin.collections.filter
import kotlin.collections.toTypedArray

object PermissionHelper {

    fun getAllRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        permissions += Manifest.permission.CAMERA
        permissions += Manifest.permission.ACCESS_FINE_LOCATION

        if (Build.VERSION.SDK_INT >= 34) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
        } else if (Build.VERSION.SDK_INT >= 31) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }

        return permissions.toTypedArray()
    }


    fun checkAndRequestAllPermissions(
        context: Context,
        launcher: ActivityResultLauncher<Array<String>>
    ): Boolean {
        val permissions = getAllRequiredPermissions()
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (missing.isEmpty()) {
            true
        } else {
            launcher.launch(missing.toTypedArray())
            false
        }
    }
}