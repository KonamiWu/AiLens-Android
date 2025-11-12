package com.konami.ailens.translation.dialog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.databinding.FragmentDialogTranslationSettingBinding
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.DialogTranslationCapability
import com.konami.ailens.translation.LanguageSelectionFragment
import com.konami.ailens.translation.VerticalSpaceItemDecoration

class DialogTranslationSettingFragment: Fragment() {
    private lateinit var binding: FragmentDialogTranslationSettingBinding
    private lateinit var adapter: Adapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDialogTranslationSettingBinding.inflate(inflater, container, false)
        adapter = Adapter(inflater.context) { isSource ->
            showLanguageSelection(isSource)
        }
        binding.recyclerView.adapter = adapter

        val decoration = VerticalSpaceItemDecoration(requireContext().resources.getDimension(R.dimen.dialog_translation_setting_item_vertical_space).toInt())
        binding.recyclerView.addItemDecoration(decoration)

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.startButton.setOnClickListener {
            Orchestrator.instance.startDialogTranslation(DialogTranslationCapability.MicSide.TARGET)
        }

        return binding.root
    }

    private fun showLanguageSelection(isSource: Boolean) {
        val currentLanguage = if (isSource) {
            Orchestrator.instance.interpretationSourceLanguage
        } else {
            Orchestrator.instance.interpretationTargetLanguage
        }

        val dialog = LanguageSelectionFragment.newInstance(currentLanguage) { selectedLanguage ->
            if (isSource) {
                Orchestrator.instance.dialogSourceLanguage = selectedLanguage
                SharedPrefs.dialogSourceLanguage = selectedLanguage
            } else {
                Orchestrator.instance.dialogTargetLanguage = selectedLanguage
                SharedPrefs.dialogTargetLanguage = selectedLanguage
            }
            adapter.notifyItemRangeChanged(0, 4)
        }

        dialog.show(childFragmentManager, "LanguageSelection")
    }

    class Adapter(private val context: Context, private val languageOnClickAction: (Boolean) -> Unit): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
            val languageTextView: TextView = view.findViewById(R.id.sourceLanguageTextView)
            val titleTextView: TextView = view.findViewById(R.id.titleTextView)
            val infoTextView: TextView = view.findViewById(R.id.infoTextView)
            val languageButton: Button = view.findViewById(R.id.languageButton)
        }

        class RightExampleViewHoler(view: View): RecyclerView.ViewHolder(view) {
            val languageTextView: TextView = view.findViewById(R.id.languageTextView)
        }

        class LeftExampleViewHoler(view: View): RecyclerView.ViewHolder(view) {
            val sourceLanguageTextView: TextView = view.findViewById(R.id.sourceLanguageTextView)
            val targetLanguageTextView: TextView = view.findViewById(R.id.targetLanguageTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view: View

            when (viewType) {
                0 -> {
                    view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_translation_setting_right_item, parent, false)
                    return ViewHolder(view)
                }
                1 -> {
                    view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_translation_setting_left_item, parent, false)
                    return ViewHolder(view)
                }
                2 -> {
                    view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_translation_setting_right_example_item, parent, false)
                    return RightExampleViewHoler(view)
                }
                else -> {
                    view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_translation_setting_left_example_item, parent, false)
                    return LeftExampleViewHoler(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val viewType = getItemViewType(position)

            when (viewType) {
                0 -> {
                    val holder = holder as ViewHolder
                    val language = Orchestrator.instance.dialogTargetLanguage
                    holder.languageTextView.text = language.title
                    holder.titleTextView.text = context.resources.getString(R.string.phone)
                    holder.languageButton.setOnClickListener {
                        languageOnClickAction(false)
                    }
                    when (language) {
                        Orchestrator.Language.ENGLISH -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_phone_en)
                        }
                        Orchestrator.Language.ESPANOL -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_phone_es)
                        }
                        Orchestrator.Language.FRANCAIS -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_phone_fr)
                        }
                        Orchestrator.Language.CHINESE -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_phone_zh)
                        }
                        Orchestrator.Language.JAPANESE -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_phone_ja)
                        }
                    }
                }
                1 -> {
                    val holder = holder as ViewHolder
                    val language = Orchestrator.instance.dialogSourceLanguage
                    holder.languageTextView.text = language.title
                    holder.titleTextView.text = context.resources.getString(R.string.glasses)
                    holder.languageButton.setOnClickListener {
                        languageOnClickAction(true)
                    }
                    when (language) {
                        Orchestrator.Language.ENGLISH -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_glasses_en)
                        }
                        Orchestrator.Language.ESPANOL -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_glasses_es)
                        }
                        Orchestrator.Language.FRANCAIS -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_glasses_fr)
                        }
                        Orchestrator.Language.CHINESE -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_glasses_zh)
                        }
                        Orchestrator.Language.JAPANESE -> {
                            holder.infoTextView.text = context.resources.getString(R.string.dialog_setting_info_glasses_ja)
                        }
                    }
                }
                2 -> {
                    val holder = holder as RightExampleViewHoler
                    val language = Orchestrator.instance.dialogTargetLanguage
                    holder.languageTextView.text = language.title
                }
                else -> {
                    val holder = holder as LeftExampleViewHoler
                    val targetLanguage = Orchestrator.instance.dialogTargetLanguage
                    val sourceLanguage = Orchestrator.instance.dialogSourceLanguage

                    holder.sourceLanguageTextView.text = sourceLanguage.title
                    holder.targetLanguageTextView.text = targetLanguage.title
                }
            }
        }

        override fun getItemCount(): Int {
            return 4
        }

        override fun getItemViewType(position: Int): Int {
            return position
        }
    }
}