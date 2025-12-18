package com.konami.ailens.setting

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.api.SessionManager
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.command.UnbindCommand
import com.konami.ailens.databinding.FragmentSettingBinding
import com.konami.ailens.device.AddDeviceActivity
import com.konami.ailens.login.LoginActivity
import com.konami.ailens.navigation.VerticalDivider
import com.konami.ailens.resolveAttrColor
import com.konami.ailens.setting.item.SettingItem
import com.konami.ailens.setting.item.SettingViewHolder
import com.konami.ailens.setting.item.SimpleSettingItem
import com.konami.ailens.setting.item.SwitchSettingItem

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

            SharedPrefs.cleanUp()
            BLEService.instance.clearSession()

            // Navigate to AddDeviceActivity
            val intent = Intent(requireActivity(), AddDeviceActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            val options = android.app.ActivityOptions.makeCustomAnimation(
                requireContext(),
                R.anim.flip_in,
                R.anim.flip_out
            )

            startActivity(intent, options.toBundle())
        }

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.editNameButton.setOnClickListener {
            findNavController().navigate(R.id.action_SettingFragment_to_EditDeviceNameFragment)
        }

        val settingListItems: List<SettingItem> = listOf(
            SwitchSettingItem(
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_setting_notification),
                title = getString(R.string.setting_notification),
                isOn = false,
                onToggle = { isOn ->
                    Log.e("SettingFragment", "Notification: $isOn")
                }
            ),

            SimpleSettingItem(
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_setting_brightness),
                title = getString(R.string.setting_brightness),
                onClick = {
                    findNavController().navigate(R.id.action_SettingFragment_to_DisplayBrightnessFragment)
                }
            ),

            SimpleSettingItem(
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_setting_power_saving),
                title = getString(R.string.setting_power_saving),
                onClick = {
                    findNavController().navigate(R.id.action_SettingFragment_to_PowerSavingFragment)
                }
            ),

            SimpleSettingItem(
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_setting_check_update),
                title = getString(R.string.setting_check_for_update),
                onClick = {
                    findNavController().navigate(R.id.action_SettingFragment_to_CheckUpdateFragment)
                }
            ),

            SimpleSettingItem(
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_setting_factory_reset),
                title = getString(R.string.setting_factory_reset),
                onClick = {
                    findNavController().navigate(R.id.action_SettingFragment_to_FactoryResetFragment)
                }
            ),

            SimpleSettingItem(
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_setting_about),
                title = getString(R.string.setting_about),
                onClick = {
                    findNavController().navigate(R.id.action_SettingFragment_to_AboutFragment)
                }
            ),

            SimpleSettingItem(
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_setting_about),
                title = getString(R.string.setting_logout),
                onClick = {
                    BLEService.instance.clearSession()
                    SessionManager.logout()
                    SharedPrefs.instance.cleanUp()
                    val intent = Intent(requireActivity(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                    val options = android.app.ActivityOptions.makeCustomAnimation(
                        requireContext(),
                        R.anim.flip_in,
                        R.anim.flip_out
                    )

                    startActivity(intent, options.toBundle())
                }
            )
        )

        val adapter = Adapter(settingListItems)
        binding.recyclerView.adapter = adapter

        val divider = VerticalDivider(inflater.context.resources.displayMetrics.density.toInt(), inflater.context.resolveAttrColor(R.attr.appBorderDarkGray))
        binding.recyclerView.addItemDecoration(divider)

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val name = SharedPrefs.deviceName
        if (name == null)
            binding.nameTextView.text = BLEService.instance.connectedSession.value?.device?.name
        else
            binding.nameTextView.text = name
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

    class Adapter(private val items: List<SettingItem>): RecyclerView.Adapter<SettingViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return items[position].viewType
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
            val item = items.first { it.viewType == viewType }
            return item.createViewHolder(parent)
        }

        override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
            items[position].bind(holder)
        }

        override fun getItemCount(): Int = items.size
    }
}