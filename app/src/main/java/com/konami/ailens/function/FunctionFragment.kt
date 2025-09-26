package com.konami.ailens.function

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.ble.BLEService
import com.konami.ailens.databinding.FragmentFunctionBinding

class FunctionFragment : Fragment() {
    private var _binding: FragmentFunctionBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FunctionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFunctionBinding.inflate(inflater, container, false)
        recyclerView = binding.recyclerView

        val aiLens = BLEService.instance.connectedSession.value!!
        adapter = FunctionAdapter(aiLens, findNavController())
        recyclerView.adapter = adapter


        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        val aiLens = BLEService.instance.connectedSession.value ?: return
        BLEService.instance.getSession(aiLens.device.address)?.stopCommands()
    }
}
