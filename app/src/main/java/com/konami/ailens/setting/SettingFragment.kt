package com.konami.ailens.setting

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.command.UnbindCommand
import com.konami.ailens.databinding.FragmentSettingBinding
import com.konami.ailens.databinding.SettingListItemBinding
import com.konami.ailens.device.AddDeviceActivity
import com.konami.ailens.navigation.VerticalDivider
import com.konami.ailens.resolveAttrColor

@SuppressLint("MissingPermission")
class SettingFragment: Fragment() {
    private lateinit var binding: FragmentSettingBinding
    private val settingListItems: List<SettingListItem>
        get() {
            return listOf(
                PowerSavingSettingListItem(findNavController()),
                CheckUpdateSettingListItem(findNavController()),
                FactoryResetSettingListItem(findNavController()),
                AboutSettingListItem(findNavController()))
        }

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

            val options = android.app.ActivityOptions.makeCustomAnimation(
                requireContext(),
                R.anim.flip_in,
                R.anim.flip_out
            )

            startActivity(intent, options.toBundle())
            requireActivity().finish()
        }

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        val adapter = Adapter(settingListItems)
        binding.recyclerView.adapter = adapter

        val divider = VerticalDivider(inflater.context.resources.displayMetrics.density.toInt(), inflater.context.resolveAttrColor(R.attr.appBorderDarkGray))
        binding.recyclerView.addItemDecoration(divider)

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

    class Adapter(private val items: List<SettingListItem>): RecyclerView.Adapter<Adapter.Holder>() {

        class Holder(view: View): RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.imageView)
            val titleTextView: TextView = view.findViewById(R.id.titleTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = SettingListItemBinding.inflate(LayoutInflater.from(parent.context))
            return Holder(binding.root)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.imageView.setImageDrawable(item.icon)
            holder.titleTextView.text = item.title
        }

        override fun getItemCount(): Int = items.size
    }
}