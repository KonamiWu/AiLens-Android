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
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.konami.ailens.agent.AgentForegroundService
import com.konami.ailens.ble.BLEForegroundService
import com.konami.ailens.ble.BLEService
import com.konami.ailens.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    // Permission launcher to request multiple permissions at once
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

        // Check and request permissions at startup
        val hasPermissions = PermissionHelper.checkAndRequestAllPermissions(this, permissionLauncher)
        if (hasPermissions) {
            startBleService(withBleAction = true)
            startAgentService()
        }
    }

    override fun onResume() {
        super.onResume()
        // Double-check in case user enabled permissions in system settings
        val hasAllPermissions = PermissionHelper.hasAllPermissions(this)
        if (hasAllPermissions) {
            startBleService(withBleAction = true)
            startAgentService()
        }
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
}
