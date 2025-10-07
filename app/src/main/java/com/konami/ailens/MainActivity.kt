package com.konami.ailens

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.konami.ailens.agent.AgentForegroundService
import com.konami.ailens.agent.AgentService
import com.konami.ailens.agent.Environment
import com.konami.ailens.ble.BLEForegroundService
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.databinding.ActivityMainBinding
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.role.AgentRole
import com.konami.ailens.orchestrator.role.BluetoothRole
import com.konami.ailens.recorder.BluetoothRecorder
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val token = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImxJN3FwamhzWFQ5RXJRMmMiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL3phZXJvbG94aXJpbXNkcnVyem5lLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiJmNGNhMmQwNi1iZGFiLTQxODEtYmY1My03MjUyZjQ4NjRjZjIiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzU5ODI0NDE3LCJpYXQiOjE3NTkyMTk2MTcsImVtYWlsIjoia29uYW1pQHRoaW5rYXIuY29tIiwicGhvbmUiOiIiLCJhcHBfbWV0YWRhdGEiOnsicHJvdmlkZXIiOiJlbWFpbCIsInByb3ZpZGVycyI6WyJlbWFpbCJdfSwidXNlcl9tZXRhZGF0YSI6eyJlbWFpbCI6ImtvbmFtaUB0aGlua2FyLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJwaG9uZV92ZXJpZmllZCI6ZmFsc2UsInN1YiI6ImY0Y2EyZDA2LWJkYWItNDE4MS1iZjUzLTcyNTJmNDg2NGNmMiJ9LCJyb2xlIjoiYXV0aGVudGljYXRlZCIsImFhbCI6ImFhbDEiLCJhbXIiOlt7Im1ldGhvZCI6InBhc3N3b3JkIiwidGltZXN0YW1wIjoxNzU5MjE5NjE3fV0sInNlc3Npb25faWQiOiIyOGY3MmUxNC0xYmE2LTQxNmMtOWNmZC1iYTBkMWU1ODIwZTciLCJpc19hbm9ueW1vdXMiOmZhbHNlfQ.JmL6DhaKbAVgLbImgBqh2IOUceBOObc05GW2iPurL3Y"
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val orchestrator = Orchestrator.instance

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val deniedPermissions = result.filterValues { granted -> !granted }.keys
            if (deniedPermissions.isEmpty()) {
                Log.e("PermissionHelper", "All permissions granted")
                startBleService(withBleAction = true)
                startAgentService()
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

        // Initialize BLE singleton service
        BLEService.getOrCreate(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Navigation SDK and show terms if needed
        initializeNavigationSDK()

        // Check and request permissions at startup
        val hasPermissions = PermissionHelper.checkAndRequestAllPermissions(this, permissionLauncher)
        if (hasPermissions) {
            startBleService(withBleAction = true)
            startAgentService()
            collectBLE()
        }
    }
    
    private fun initializeNavigationSDK() {
        Log.d("MainActivity", "Initializing Navigation SDK")
        
        // Try to initialize navigator directly
        // Terms will be shown automatically if needed
        NavigationApi.getNavigator(
            this,
            object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(navigator: Navigator) {
                    Log.d("MainActivity", "Navigator initialized successfully")
                    // Clean up since we just needed to initialize and accept terms
                    navigator.cleanup()
                }
                
                override fun onError(errorCode: Int) {
                    val errorMessage = when (errorCode) {
                        NavigationApi.ErrorCode.NOT_AUTHORIZED -> "NOT_AUTHORIZED"
                        NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> "TERMS_NOT_ACCEPTED"
                        NavigationApi.ErrorCode.NETWORK_ERROR -> "NETWORK_ERROR"
                        NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> "LOCATION_PERMISSION_MISSING"
                        else -> "Unknown error: $errorCode"
                    }
                    Log.e("MainActivity", "Failed to initialize Navigator: $errorMessage")
                    
                    if (errorCode == NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED) {
                        Toast.makeText(
                            this@MainActivity,
                            "Please accept terms to use navigation features",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    private fun startBleService(withBleAction: Boolean) {
        val bleIntent = Intent(this, BLEForegroundService::class.java)
        if (withBleAction) {
            bleIntent.action = BLEForegroundService.ACTION_START_BLE
        }
        ContextCompat.startForegroundService(this, bleIntent)
    }

    private fun startAgentService() {
        val agentIntent = Intent(this, AgentForegroundService::class.java)
        ContextCompat.startForegroundService(this, agentIntent)
        val service = AgentService.instance
        service.connect(
            token,
            Environment.Dev.config,
            "agent",
            "en"
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collectBLE() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val service = BLEService.getOrCreate(applicationContext)
                service.connectedSession
                    .flatMapLatest { session ->
                        if (session == null) {
                            orchestrator.clean()
                            emptyFlow()
                        } else {
                            session.state
                        }
                    }
                    .collect { state ->
                        when (state) {
                            DeviceSession.State.CONNECTED -> {
                                val session = service.connectedSession.value ?: return@collect
                                val bluetoothRole = BluetoothRole(session, orchestrator)
                                val agentRole = AgentRole(orchestrator, BluetoothRecorder(session))
                                orchestrator.register(bluetoothRole)
                                orchestrator.register(agentRole)
                            }
                            DeviceSession.State.DISCONNECTED -> {
                                orchestrator.clean()
                            }
                            else -> {}
                        }
                    }
            }
        }
    }
}
