package com.konami.ailens.device

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentConnectionFailedBinding

class ConnectionFailedFragment: Fragment() {
    private lateinit var binding: FragmentConnectionFailedBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentConnectionFailedBinding.inflate(inflater, container, false)
        binding.returnButton.setOnClickListener {
            findNavController().popBackStack(R.id.addDeviceSearchFragment, false)
        }
        return binding.root
    }
}