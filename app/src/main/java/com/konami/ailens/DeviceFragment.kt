package com.konami.ailens

import AiLens
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.databinding.FragmentDeviceBinding
import kotlinx.coroutines.launch
import kotlin.getValue

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class DeviceFragment : Fragment() {
    private val viewModel: DeviceViewModel by viewModels()
    private var _binding: FragmentDeviceBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private val adapter = DeviceAdapter {
        itemClick(it)
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceBinding.inflate(inflater, container, false)
        recyclerView = binding.recyclerView
        recyclerView.adapter = adapter


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.availableGlasses.collect {
                    adapter.update(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lastGlasses.collect {
                    adapter.update(it)
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


    }

    private fun itemClick(glasses: AiLens) {
        viewModel.actionItem(glasses)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}