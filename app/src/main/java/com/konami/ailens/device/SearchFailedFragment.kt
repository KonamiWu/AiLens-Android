package com.konami.ailens.device

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.ble.BLEService
import com.konami.ailens.databinding.FragmentSearchFailedBinding

class SearchFailedFragment: Fragment() {
    private lateinit var binding: FragmentSearchFailedBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSearchFailedBinding.inflate(inflater, container, false)
        binding.unbindButton.setOnClickListener {
            BLEService.instance.clearSession()
            navigateToBluetoothSettings()
        }

        binding.tryAgainButton.setOnClickListener {
            BLEService.instance.clearSession()
            findNavController().popBackStack()
        }

        return binding.root
    }

    private fun navigateToBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        startActivity(intent)
    }
}