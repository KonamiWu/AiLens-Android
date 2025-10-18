package com.konami.ailens

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.konami.ailens.databinding.ActivityMainBinding
import com.konami.ailens.orchestrator.Orchestrator
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.konami.ailens.ble.BLEService
import com.konami.ailens.navigation.NavigationService

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val orchestrator = Orchestrator.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NavigationService.instance.initializeNavigator(this)
//        BLEService.instance.retrieve()
//        initializeNavigationSDK()
    }

    private fun initializeNavigationSDK() {

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
}
