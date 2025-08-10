package com.konami.ailens.device

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentDeviceBinding
import kotlinx.coroutines.launch

class DeviceFragment : Fragment() {
    private val viewModel: DeviceViewModel by viewModels()
    private var _binding: FragmentDeviceBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private val adapter = DeviceAdapter { viewModel.actionItem(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceBinding.inflate(inflater, container, false)
        recyclerView = binding.recyclerView
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiItems.collect { items -> adapter.submitList(items) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvent.collect { items ->
                    findNavController().navigate(R.id.action_DeviceFragment_to_FunctionFragment)
                }
            }
        }

        return binding.root
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}