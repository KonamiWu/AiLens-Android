package com.konami.ailens.device

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.konami.ailens.AppForegroundService
import com.konami.ailens.MainActivity
import com.konami.ailens.PermissionHelper
import com.konami.ailens.SharedPrefs
import com.konami.ailens.agent.AgentService
import com.konami.ailens.agent.Environment
import com.konami.ailens.ble.BLEService
import com.konami.ailens.databinding.ActivityAddDeviceBinding

class AddDeviceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddDeviceBinding
    private val token = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImxJN3FwamhzWFQ5RXJRMmMiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL3phZXJvbG94aXJpbXNkcnVyem5lLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiJiODczMGNjYi01YjkxLTRkNGItOTA4MS0xZmExYjBiYzRmYjEiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzYxODIwNjYzLCJpYXQiOjE3NjEyMTU4NjMsImVtYWlsIjoiamVubnlAdGhpbmthci5jb20iLCJwaG9uZSI6IiIsImFwcF9tZXRhZGF0YSI6eyJwcm92aWRlciI6ImVtYWlsIiwicHJvdmlkZXJzIjpbImVtYWlsIl19LCJ1c2VyX21ldGFkYXRhIjp7ImVtYWlsIjoiamVubnlAdGhpbmthci5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwicGhvbmVfdmVyaWZpZWQiOmZhbHNlLCJzdWIiOiJiODczMGNjYi01YjkxLTRkNGItOTA4MS0xZmExYjBiYzRmYjEifSwicm9sZSI6ImF1dGhlbnRpY2F0ZWQiLCJhYWwiOiJhYWwxIiwiYW1yIjpbeyJtZXRob2QiOiJwYXNzd29yZCIsInRpbWVzdGFtcCI6MTc2MTIxNTg2M31dLCJzZXNzaW9uX2lkIjoiZjQyOGE4ZmQtY2EwYS00YTRiLTg4N2EtMGIyOWZjYWRiYjdmIiwiaXNfYW5vbnltb3VzIjpmYWxzZX0.QAi8KDTBK5U5TNkAj_9Xsm0Qv4A8N_arDmPhXQHveYY"
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val deniedPermissions = result.filterValues { granted -> !granted }.keys
            if (deniedPermissions.isEmpty()) {
                Log.e("PermissionHelper", "All permissions granted")
                startBleService(withBleAction = true)
                // Agent service will be started when glasses connect
            } else {
                Log.e("PermissionHelper", "Denied: $deniedPermissions")
                Toast.makeText(
                    this,
                    "Permission not enough: $deniedPermissions",
                    Toast.LENGTH_SHORT
                ).show()
                startBleService(withBleAction = false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasPermissions = PermissionHelper.checkAndRequestAllPermissions(this, permissionLauncher)
        if (hasPermissions) {
            startBleService(withBleAction = true)
            // Agent service will be started when glasses connect
        }
        // Check if device is already paired
        val deviceInfo = SharedPrefs.getDeviceInfo(this)
        if (deviceInfo != null) {
            // BLEService is already initialized in Application
            BLEService.instance.retrieve()
            // Device already paired, go directly to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
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

    private fun startBleService(withBleAction: Boolean) {
        AppForegroundService.startFeature(this, AppForegroundService.Feature.BLE)
    }

    private fun startAgentService() {
        AppForegroundService.startFeature(this, AppForegroundService.Feature.AGENT)
        val service = AgentService.instance
        service.connect(
            token,
            Environment.Dev.config,
            "agent",
            "en"
        )
    }
}