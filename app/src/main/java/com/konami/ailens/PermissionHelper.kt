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

        if (Build.VERSION.SDK_INT >= 33) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (Build.VERSION.SDK_INT >= 31) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return permissions.toTypedArray()
    }

    fun missingPermissions(context: Context): List<String> =
        getAllRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun hasAllPermissions(context: Context): Boolean = missingPermissions(context).isEmpty()

    fun checkAndRequestAllPermissions(
        context: Context,
        launcher: ActivityResultLauncher<Array<String>>
    ): Boolean {
        val missing = missingPermissions(context)
        return if (missing.isEmpty()) {
            true
        } else {
            launcher.launch(missing.toTypedArray())
            false
        }
    }
}

