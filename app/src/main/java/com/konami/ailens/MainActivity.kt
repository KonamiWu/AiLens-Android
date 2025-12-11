package com.konami.ailens

import android.content.Intent
import android.os.Build
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
import com.konami.ailens.databinding.ActivityMainBinding
import com.konami.ailens.orchestrator.Orchestrator
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.konami.ailens.ble.BLEService
import com.konami.ailens.navigation.NavigationService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val orchestrator = Orchestrator.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.e("TAG", "MainActivity MainActivity MainActivity MainActivity MainActivity")
        NavigationService.instance.initializeNavigator(this)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                orchestrator.uiNavigationEvent.collect { event ->
                    val navController = findNavController(R.id.nav_host_fragment_content_main)
                    when (event) {
                        is Orchestrator.UINavigationEvent.NavigateToNavigation -> {
                        }
                        is Orchestrator.UINavigationEvent.Interpretation -> {
                            navController.navigate(R.id.action_global_to_InterpretationFragment)
                        }
                        is Orchestrator.UINavigationEvent.DialogTranslation -> {
                            val bundle = Bundle().apply {
                                putString("START_SIDE", event.startSide.name)
                            }
                            navController.navigate(R.id.action_global_to_DialogTranslationFragment, bundle)
                        }
                        is Orchestrator.UINavigationEvent.NavigateToAgent -> {

                        }
                    }
                }
            }
        }
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
