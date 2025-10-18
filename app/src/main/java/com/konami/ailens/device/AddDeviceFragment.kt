package com.konami.ailens.device

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentAddDeviceBinding

class AddDeviceFragment: Fragment() {
    private lateinit var binding: FragmentAddDeviceBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAddDeviceBinding.inflate(inflater, container, false)
        binding.addButton.setOnClickListener {
            findNavController().navigate(R.id.action_AddDeviceFragment_to_AddDeviceSearchFragment)
        }
        return binding.root
    }
}