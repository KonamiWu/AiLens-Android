package com.konami.ailens.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R

class NavigationGuidanceFragment : Fragment() {
    
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Navigate back to HomeFragment when back button is pressed
            navigateToHome()
        }
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_navigation_guidance, container, false)
        val mapContainer = root.findViewById<ViewGroup>(R.id.guidanceMapContainer)
        NavigationService.attachTo(mapContainer, viewLifecycleOwner, requireContext())
        return root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Register the back pressed callback
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }
    
    private fun navigateToHome() {
        // Navigate back to HomeFragment
        findNavController().navigate(R.id.HomeFragment)
    }
}
