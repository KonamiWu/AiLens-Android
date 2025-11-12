package com.konami.ailens.translation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentSmartTranslationBinding

class SmartTranslationFragment: Fragment() {
    private lateinit var binding: FragmentSmartTranslationBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSmartTranslationBinding.inflate(inflater, container, false)
        binding.interpretationButton.setOnClickListener {
            findNavController().navigate(R.id.action_SmartTranslationFragment_to_InterpretationMainFragment)
        }

        binding.twoWayButton.setOnClickListener {
            findNavController().navigate(R.id.action_SmartTranslationFragment_to_DialogTranslationSettingFragment)
        }

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        return binding.root
    }
}