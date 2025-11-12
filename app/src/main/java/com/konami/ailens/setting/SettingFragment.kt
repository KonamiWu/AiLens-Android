package com.konami.ailens.setting

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.SharedPrefs
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.command.UnbindCommand
import com.konami.ailens.databinding.FragmentSettingBinding
import com.konami.ailens.device.AddDeviceActivity

@SuppressLint("MissingPermission")
class SettingFragment: Fragment() {
    private lateinit var binding: FragmentSettingBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSettingBinding.inflate(inflater, container, false)

        binding.unbindButton.setOnClickListener {
            val session = BLEService.instance.connectedSession.value

            session?.let {
                removeBond(it.device)
                it.disconnect()
            }

            SharedPrefs.clearDeviceInfo()
            BLEService.instance.clearSession()

            // Navigate to AddDeviceActivity
            val intent = Intent(requireActivity(), AddDeviceActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        return binding.root
    }

    private fun removeBond(device: BluetoothDevice) {
        try {
            val command = UnbindCommand()
            command.completion = {
                val method = device.javaClass.getMethod("removeBond")
                val result = method.invoke(device)
            }
            BLEService.instance.connectedSession.value?.add(command)

        } catch (e: Exception) {
            Log.e("SettingFragment", "Error removing bond: ${e.message}", e)
        }
    }
}