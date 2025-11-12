package com.konami.ailens.device

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.ble.BLEService
import com.konami.ailens.ble.Glasses
import com.konami.ailens.databinding.FragmentDeviceListBinding
import com.konami.ailens.translation.VerticalSpaceItemDecoration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@SuppressLint("MissingPermission", "NotifyDataSetChanged")
class DeviceListFragment: Fragment() {
    private lateinit var binding: FragmentDeviceListBinding
    private val viewModel: DeviceListViewModel by viewModels()
    private val adapter = DeviceListAdapter { deviceSession ->
        if (deviceSession.state.value != Glasses.State.AVAILABLE)
            return@DeviceListAdapter
        if (deviceSession.device.bondState == BluetoothDevice.BOND_BONDED) {
            removeBond(deviceSession)
        }
        viewModel.connect(deviceSession)

        refresh()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDeviceListBinding.inflate(inflater, container, false)
        binding.recyclerView.adapter = adapter

        // Add 16dp divider between items
        val dividerHeight = resources.getDimensionPixelSize(R.dimen.device_list_item_divider)
        binding.recyclerView.addItemDecoration(VerticalSpaceItemDecoration(dividerHeight))

        binding.tryAgainButton.setOnClickListener {
            findNavController().popBackStack()
        }

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter.updateItems(viewModel.getItems())
        adapter.notifyDataSetChanged()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.updateFlow.collect {
                    adapter.updateItems(viewModel.getItems())
                    adapter.notifyDataSetChanged()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deviceConnectedFlow.collect {
                    if (it) {
                        findNavController().navigate(R.id.action_DeviceListFragment_to_ConnectedSuccessfullyFragment)
                    } else {
                        findNavController().navigate(R.id.action_DeviceListFragment_to_ConnectionFailedFragment)
                    }
                }
            }
        }

        // Stop search and show try again button after 10 seconds
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(5000)
                viewModel.stopSearch()
                binding.tryAgainLayout.visibility = View.VISIBLE
                binding.tryAgainLayout.alpha = 0f
                binding.tryAgainLayout.animate().alpha(1f).setDuration(1000).start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    /**
     * Remove bond (unpair) a Bluetooth device
     */
    private fun removeBond(deviceSession: Glasses) {
        val device = deviceSession.device
        val deviceName = device.name ?: device.address

        try {
            // Check if this is the currently saved device
            val savedDeviceInfo = SharedPrefs.getDeviceInfo()
            val isCurrentDevice = savedDeviceInfo?.mac == device.address

            // If currently connected, disconnect first
            if (isCurrentDevice) {
                val connectedSession = BLEService.instance.connectedSession.value
                if (connectedSession?.device?.address == device.address) {
                    connectedSession.disconnect()
                }
            }

            // Use reflection to call hidden removeBond() method
            val removeBondMethod = device.javaClass.getMethod("removeBond")
            val result = removeBondMethod.invoke(device) as? Boolean ?: false

            if (result) {
                if (isCurrentDevice) {
                    SharedPrefs.clearDeviceInfo()
                }
                // Refresh the list after a short delay to allow the bond state to update
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(500)
                    refresh()
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceListFragment", "Error removing bond: ${e.message}", e)
        }
    }

    private fun refresh() {
        adapter.notifyDataSetChanged()
    }

    class DeviceListAdapter(val onclick: (Glasses) -> Unit): RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {
        private var items = listOf<Glasses>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.device_list_item, parent, false)

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = items[position]
            holder.nameTextView.text = session.device.name
            holder.itemView.setOnClickListener {
                onclick.invoke(session)
            }

            when (session.state.value) {
                Glasses.State.CONNECTING, Glasses.State.PAIRING -> {
                    holder.rotateLayout.visibility = View.VISIBLE
                    holder.connectLayout.visibility = View.GONE
                    if (holder.rotateAnim?.isRunning != true) {
                        holder.rotateAnim = ObjectAnimator.ofFloat(holder.rotateImageView, "rotation", 0f, 360f)
                        holder.rotateAnim?.duration = 1000
                        holder.rotateAnim?.repeatCount = ValueAnimator.INFINITE
                        holder.rotateAnim?.interpolator = LinearInterpolator()
                        holder.rotateAnim?.start()
                    }
                }
                else -> {
                    holder.rotateAnim?.cancel()
                    holder.rotateAnim = null
                    holder.rotateLayout.visibility = View.GONE
                    holder.connectLayout.visibility = View.VISIBLE
                }
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        fun updateItems(items: List<Glasses>) {
            this.items = items
        }

        class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
            val nameTextView: TextView = view.findViewById(R.id.nameTextView)
            val connectLayout: View = view.findViewById(R.id.buttonLayout)
            val rotateLayout: View = view.findViewById(R.id.loadingLayout)
            val rotateImageView: ImageView = view.findViewById(R.id.loadingImageView)
            var rotateAnim: ObjectAnimator? = null
        }
    }
}