package com.konami.ailens.setting

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.DeviceInfoReader
import com.konami.ailens.databinding.FragmentAboutBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutFragment: Fragment() {
    private lateinit var binding: FragmentAboutBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAboutBinding.inflate(inflater, container, false)

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        setupAppVersion()
        loadDeviceInfo()

        return binding.root
    }

    private fun setupAppVersion() {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.appVersionTextView.text = "V${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            binding.appVersionTextView.text = "V1.0"
        }
    }

    private fun loadDeviceInfo() {
        val glasses = BLEService.instance.connectedSession.value


        if (glasses == null) {
            binding.firmwareVersionTextView.text = "-"
            binding.bluetoothAddressTextView.text = "-"
            return
        }

        binding.bluetoothAddressTextView.text = glasses.device.address ?: "-"

        DeviceInfoReader.readDeviceInfo(glasses) { result ->
            result.onSuccess { deviceInfo ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        binding.firmwareVersionTextView.text = deviceInfo.version
                    }
                }
            }.onFailure {
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        binding.firmwareVersionTextView.text = "-"
                    }
                }
            }
        }
    }
}
