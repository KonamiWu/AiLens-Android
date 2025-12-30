package com.konami.ailens.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentHomeBinding
import com.konami.ailens.view.BlurHostLayout

@SuppressLint("MissingPermission")
class HomeFragment : Fragment(), TabBarConfigurable {

    private lateinit var binding: FragmentHomeBinding

    override fun shouldShowTabBar(): Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        // (可選) 如果你 foreground 需要吃 status bar inset，建議只套在 blurForeground
        // 避免 backdrop 也被推下來
//        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
//            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(v.paddingLeft, sysBars.top, v.paddingRight, sysBars.bottom)
//            insets
//        }

        binding.settingLayout.setOnClickListener {
            findNavController().navigate(R.id.action_HomeFragment_to_SettingFragment)
        }

        return binding.root
    }

}
