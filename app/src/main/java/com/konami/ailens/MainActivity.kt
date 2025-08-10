package com.konami.ailens

import android.content.Intent
import android.os.Bundle
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
import com.konami.ailens.ble.BLEForegroundService
import com.konami.ailens.ble.BLEService
import com.konami.ailens.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher =
        this.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val allGranted = result.all { it.value }
            if (allGranted) {
                startBleService(withBleAction = true)
            } else {
                Toast.makeText(this, "Permission not enough", Toast.LENGTH_SHORT).show()
                startBleService(withBleAction = false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        BLEService.getOrCreate(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        if (PermissionHelper.checkAndRequestAllPermissions(this, permissionLauncher)) {
            startBleService(withBleAction = true)
        }
    }

    private fun startBleService(withBleAction: Boolean) {
        val bleIntent = Intent(this, BLEForegroundService::class.java).apply {
            action = if (withBleAction) BLEForegroundService.ACTION_START_BLE else null
        }
        ContextCompat.startForegroundService(this, bleIntent)
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
