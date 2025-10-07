package com.konami.ailens.main

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentHomeBinding
import com.konami.ailens.navigation.NavigationService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class HomeFragment: Fragment() {
    private lateinit var binding: FragmentHomeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.navigationButton.setOnClickListener {
            navigateToNavigationScreen()
        }
        return binding.root
    }
    
    private fun navigateToNavigationScreen() {
        lifecycleScope.launch {
            try {
                val isNavigating = NavigationService.isNavigating
                if (isNavigating) {
                    findNavController().navigate(R.id.navigationGuidanceFragment)
                } else {
                    findNavController().navigate(R.id.AddressPickerFragment)
                }
            } catch (e: Exception) {
                findNavController().navigate(R.id.AddressPickerFragment)
            }
        }
    }
}