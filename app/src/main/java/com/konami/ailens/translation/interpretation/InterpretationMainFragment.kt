package com.konami.ailens.translation.interpretation

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentInterpretationMainBinding
import com.konami.ailens.extension.crossfadeOverlay
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.selection.SelectionFragment
import kotlin.math.PI

class InterpretationMainFragment: Fragment() {
    private lateinit var binding: FragmentInterpretationMainBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentInterpretationMainBinding.inflate(inflater, container, false)
        binding.bilingualSwitch.setOnCheckedChangeListener { _, isChecked ->
            Orchestrator.instance.bilingual = isChecked
        }

        updateLanguageText(false)

        binding.exchangeButton.setOnClickListener {
            binding.exchangeButton.isEnabled = false
            binding.exchangeImageView.animate().rotationBy(180f).scaleX(1.4f).scaleY(1.4f).setDuration(300).setInterpolator(LinearInterpolator()).withEndAction {
                binding.exchangeImageView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotationBy(180f)
                    .setDuration(300)
                    .setInterpolator(LinearInterpolator()).withEndAction { binding.exchangeButton.isEnabled = true }
                    .start()
            }.start()
            val temp = Orchestrator.instance.interpretationSourceLanguage
            Orchestrator.instance.interpretationSourceLanguage = Orchestrator.instance.interpretationTargetLanguage
            Orchestrator.instance.interpretationTargetLanguage = temp
            updateLanguageText(true)
        }

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.bilingualSwitch.isChecked = Orchestrator.instance.bilingual

        binding.sourceButton.setOnClickListener {
            showLanguageSelection(true)
        }

        binding.targetButton.setOnClickListener {
            showLanguageSelection(false)
        }

        return binding.root
    }

    private fun updateLanguageText(animated: Boolean) {
        if (animated) {
            binding.sourceTextView.crossfadeOverlay(Orchestrator.instance.interpretationSourceLanguage.title)
            binding.targetTextView.crossfadeOverlay(Orchestrator.instance.interpretationTargetLanguage.title)
        } else {
            binding.sourceTextView.text = Orchestrator.instance.interpretationSourceLanguage.title
            binding.targetTextView.text = Orchestrator.instance.interpretationTargetLanguage.title
        }
    }

    private fun showLanguageSelection(isSource: Boolean) {
        val currentLanguage = if (isSource) {
            Orchestrator.instance.interpretationSourceLanguage
        } else {
            Orchestrator.instance.interpretationTargetLanguage
        }

        val dialog = SelectionFragment.newInstance(
            items = Orchestrator.Language.entries.toList(),
            currentItem = currentLanguage
        ) { selectedLanguage ->
            if (isSource) {
                Orchestrator.instance.interpretationSourceLanguage = selectedLanguage
            } else {
                Orchestrator.instance.interpretationTargetLanguage = selectedLanguage
            }
            updateLanguageText(true)
        }

        dialog.show(childFragmentManager, "Selection")
    }
}
