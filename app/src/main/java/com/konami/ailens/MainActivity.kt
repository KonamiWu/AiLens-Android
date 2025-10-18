package com.konami.ailens

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.databinding.ActivityMainBinding
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.role.AgentRole
import com.konami.ailens.orchestrator.role.BluetoothRole
import com.konami.ailens.recorder.BluetoothRecorder
import com.konami.ailens.agent.AgentService
import com.konami.ailens.agent.Environment
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.konami.ailens.ble.command.ReadBatteryCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val token = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImxJN3FwamhzWFQ5RXJRMmMiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL3phZXJvbG94aXJpbXNkcnVyem5lLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiJmNGNhMmQwNi1iZGFiLTQxODEtYmY1My03MjUyZjQ4NjRjZjIiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzYxODE5NDc4LCJpYXQiOjE3NjEyMTQ2NzgsImVtYWlsIjoia29uYW1pQHRoaW5rYXIuY29tIiwicGhvbmUiOiIiLCJhcHBfbWV0YWRhdGEiOnsicHJvdmlkZXIiOiJlbWFpbCIsInByb3ZpZGVycyI6WyJlbWFpbCJdfSwidXNlcl9tZXRhZGF0YSI6eyJlbWFpbCI6ImtvbmFtaUB0aGlua2FyLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJwaG9uZV92ZXJpZmllZCI6ZmFsc2UsInN1YiI6ImY0Y2EyZDA2LWJkYWItNDE4MS1iZjUzLTcyNTJmNDg2NGNmMiJ9LCJyb2xlIjoiYXV0aGVudGljYXRlZCIsImFhbCI6ImFhbDEiLCJhbXIiOlt7Im1ldGhvZCI6InBhc3N3b3JkIiwidGltZXN0YW1wIjoxNzYxMjE0Njc4fV0sInNlc3Npb25faWQiOiI4NGQwNzJhMi0wYmYyLTRiNjQtOTE5NS05OGM3NjlhNjk2YWQiLCJpc19hbm9ueW1vdXMiOmZhbHNlfQ.a8pcB1r5TUIt6QvgZufarBjWxSIwWIM_GELPQYByYJs"
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val orchestrator = Orchestrator.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BLEService is already initialized in AiLensApplication.onCreate()
        // No need to initialize here

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Navigation SDK and show terms if needed
        initializeNavigationSDK()

        // Start monitoring BLE connection and auto-register roles to orchestrator
        // This needs to run regardless of permission status - it will only activate when connected
        collectBLE()
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

    private var agentReadyJob: kotlinx.coroutines.Job? = null
    private var currentAgentRole: com.konami.ailens.orchestrator.role.AgentRole? = null
    private var currentBluetoothRole: com.konami.ailens.orchestrator.role.BluetoothRole? = null

    private fun handleConnectionEstablished(session: DeviceSession) {
        Log.d("MainActivity", "Handling connection established")
        session.add(ReadBatteryCommand())

        // Clean up old roles before creating new ones
        Log.d("MainActivity", "Cleaning up old roles before creating new ones")
        currentAgentRole?.cleanup()
        currentAgentRole = null
        currentBluetoothRole = null
        orchestrator.clean()

        // Start Agent service when glasses connect
        AppForegroundService.startFeature(this@MainActivity, AppForegroundService.Feature.AGENT)
        AgentService.instance.connect(
            token,
            Environment.Dev.config,
            "agent",
            "en"
        )

        val bluetoothRole = BluetoothRole(session, orchestrator)
        val agentRole = AgentRole(orchestrator, BluetoothRecorder(session))
        orchestrator.register(bluetoothRole)
        orchestrator.register(agentRole)

        // Store current roles for cleanup later
        currentBluetoothRole = bluetoothRole
        currentAgentRole = agentRole

        // Update notification to show connected state
        AppForegroundService.updateBleConnectionState(this@MainActivity, true)

        // Monitor Agent ready state and update notification
        agentReadyJob?.cancel()
        agentReadyJob = lifecycleScope.launch {
            AgentService.instance.isReady.collect { isReady ->
                Log.e("MainActivity", "Agent ready state changed: $isReady")
                AppForegroundService.updateAgentReadyState(this@MainActivity, isReady)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun collectBLE() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val service = BLEService.instance

                // Check if already connected when app starts
                val initialSession = service.connectedSession.value
                if (initialSession != null && initialSession.state.value == DeviceSession.State.CONNECTED) {
                    Log.d("MainActivity", "App started with existing connection, initializing...")
                    handleConnectionEstablished(initialSession)
                }

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
                                handleConnectionEstablished(session)
                            }
                            DeviceSession.State.DISCONNECTED -> {
                                Log.e("MainActivity", "Device disconnected, cleaning orchestrator")

                                // Clean up roles and cancel their coroutines
                                currentAgentRole?.cleanup()
                                currentAgentRole = null
                                currentBluetoothRole = null
                                orchestrator.clean()

                                // Cancel agent ready monitoring
                                agentReadyJob?.cancel()
                                agentReadyJob = null

                                // Stop Agent service when glasses disconnect
                                AgentService.instance.disconnect()
                                AppForegroundService.stopFeature(this@MainActivity, AppForegroundService.Feature.AGENT)

                                // Update notification to show disconnected state
                                AppForegroundService.updateBleConnectionState(this@MainActivity, false)
                                AppForegroundService.updateAgentReadyState(this@MainActivity, false)
                            }
                            else -> {
                            }
                        }
                    }
            }
        }
    }
}
