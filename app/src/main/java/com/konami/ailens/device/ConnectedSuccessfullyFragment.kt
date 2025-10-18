package com.konami.ailens.device

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.konami.ailens.MainActivity
import com.konami.ailens.R
import com.konami.ailens.ble.BLEService
import com.konami.ailens.databinding.FragmentConnectedSuccessfullyBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class ConnectedSuccessfullyFragment: Fragment() {
    private lateinit var binding: FragmentConnectedSuccessfullyBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentConnectedSuccessfullyBinding.inflate(inflater, container, false)

        BLEService.instance.connectedSession.value?.device?.let {
            binding.nameTextView.text = it.name
        }

        return binding.root
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        if (enter && nextAnim != 0) {
            val animation = AnimationUtils.loadAnimation(requireContext(), nextAnim)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        // Wait 1 second, then change to text2_2
                        delay(1000)
                        binding.text2.text = requireContext().getString(R.string.connected_successfully_text2_2)

                        // Wait another 1 second, then change to text2_3 and navigate
                        delay(1000)
                        binding.text2.text = requireContext().getString(R.string.connected_successfully_text2_3)

                        delay(1000)
                        // Navigate to MainActivity with animation
                        val intent = Intent(requireActivity(), MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        requireActivity().finish()
                    }
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })
            return animation
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start animation sequence

    }
}