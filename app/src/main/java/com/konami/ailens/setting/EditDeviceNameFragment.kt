package com.konami.ailens.setting

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.SharedPrefs
import com.konami.ailens.ble.BLEService
import com.konami.ailens.databinding.FragmentEditDeviceNameBinding


class EditDeviceNameFragment: Fragment() {
    private lateinit var binding: FragmentEditDeviceNameBinding

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentEditDeviceNameBinding.inflate(inflater, container, false)

        val deviceName = SharedPrefs.deviceName
        val btName = BLEService.instance.connectedSession.value?.device?.name
        binding.nameEditText.hint = btName

        if (deviceName != null) {
            binding.nameEditText.setText(deviceName)
        }

        binding.saveButton.setOnClickListener {
            val value = binding.nameEditText.text.toString()
            if (value.isNotEmpty())
                SharedPrefs.deviceName = value
            else
                SharedPrefs.deviceName = btName
            hideKeyboard()
            findNavController().popBackStack()
        }

        binding.backButton.setOnClickListener {
            hideKeyboard()
            findNavController().popBackStack()
        }

        return binding.root
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}