package com.konami.ailens.device

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.konami.ailens.MainActivity
import com.konami.ailens.PermissionHelper
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.ble.AppForegroundService
import com.konami.ailens.databinding.ActivityAddDeviceBinding

class AddDeviceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddDeviceBinding
    private val token = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImxJN3FwamhzWFQ5RXJRMmMiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL3phZXJvbG94aXJpbXNkcnVyem5lLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiJiODczMGNjYi01YjkxLTRkNGItOTA4MS0xZmExYjBiYzRmYjEiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzYxODIwNjYzLCJpYXQiOjE3NjEyMTU4NjMsImVtYWlsIjoiamVubnlAdGhpbmthci5jb20iLCJwaG9uZSI6IiIsImFwcF9tZXRhZGF0YSI6eyJwcm92aWRlciI6ImVtYWlsIiwicHJvdmlkZXJzIjpbImVtYWlsIl19LCJ1c2VyX21ldGFkYXRhIjp7ImVtYWlsIjoiamVubnlAdGhpbmthci5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwicGhvbmVfdmVyaWZpZWQiOmZhbHNlLCJzdWIiOiJiODczMGNjYi01YjkxLTRkNGItOTA4MS0xZmExYjBiYzRmYjEifSwicm9sZSI6ImF1dGhlbnRpY2F0ZWQiLCJhYWwiOiJhYWwxIiwiYW1yIjpbeyJtZXRob2QiOiJwYXNzd29yZCIsInRpbWVzdGFtcCI6MTc2MTIxNTg2M31dLCJzZXNzaW9uX2lkIjoiZjQyOGE4ZmQtY2EwYS00YTRiLTg4N2EtMGIyOWZjYWRiYjdmIiwiaXNfYW5vbnltb3VzIjpmYWxzZX0.QAi8KDTBK5U5TNkAj_9Xsm0Qv4A8N_arDmPhXQHveYY"
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val deniedPermissions = result.filterValues { granted -> !granted }.keys
            if (deniedPermissions.isEmpty()) {
                Log.e("PermissionHelper", "All permissions granted")
                startAppForegroundService()
                // Agent service will be started when glasses connect
            } else {
                Log.e("PermissionHelper", "Denied: $deniedPermissions")
                Toast.makeText(
                    this,
                    "Permission not enough: $deniedPermissions",
                    Toast.LENGTH_SHORT
                ).show()
                startAppForegroundService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request battery optimization exemption first
        requestBatteryOptimizationExemption()

        val hasPermissions = PermissionHelper.checkAndRequestAllPermissions(this, permissionLauncher)
        if (hasPermissions) {
            startAppForegroundService()
        }
        // Check if device is already paired
        val deviceInfo = SharedPrefs.getDeviceInfo()
        if (deviceInfo != null) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            val options = android.app.ActivityOptions.makeCustomAnimation(
                this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )

            startActivity(intent, options.toBundle())
            finish()
            return
        }

        binding = ActivityAddDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Disable back button during device pairing process
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - ignore back button
            }
        })
    }

    private fun startAppForegroundService() {
        val intent = Intent(this, AppForegroundService::class.java)
        startForegroundService(intent)
    }

    /**
     * Request battery optimization exemption to keep Agent Service alive in background
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(PowerManager::class.java)
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Log.d("AddDeviceActivity", "Requesting battery optimization exemption")
                } catch (e: Exception) {
                    Log.e("AddDeviceActivity", "Failed to request battery optimization: ${e.message}")
                    Toast.makeText(
                        this,
                        "Please disable battery optimization for this app in Settings",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Log.d("AddDeviceActivity", "Battery optimization already disabled")
            }
        }
    }
}