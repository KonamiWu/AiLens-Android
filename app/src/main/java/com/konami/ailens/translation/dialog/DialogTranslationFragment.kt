package com.konami.ailens.translation.dialog

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentDialogTranslationBinding
import com.konami.ailens.view.VerticalDivider
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.DialogTranslationCapability
import com.konami.ailens.orchestrator.capability.DialogTranslationCapability.MicSide
import com.konami.ailens.translation.dialog.DialogTranslationViewModel
import kotlinx.coroutines.launch

class DialogTranslationFragment: Fragment() {
    private lateinit var binding: FragmentDialogTranslationBinding
    private lateinit var adapter: Adapter
    private val viewModel: DialogTranslationViewModel by viewModels()
    private var micSide = MicSide.SOURCE
    private var isRecording = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentDialogTranslationBinding.inflate(inflater, container, false)

        arguments?.getString("START_SIDE")?.let {
            micSide = MicSide.valueOf(it)
            isRecording = micSide == MicSide.TARGET
        }

        adapter = Adapter(viewModel.sourceLanguageTitle, viewModel.targetLanguageTitle)
        binding.recyclerView.adapter = adapter
        val divider = VerticalDivider(requireContext().resources.getDimension(R.dimen.dialog_translation_item_spacing).toInt(), Color.TRANSPARENT)
        binding.recyclerView.addItemDecoration(divider)

        updateMicUI()

        binding.actionButton.setOnClickListener {
            if (micSide == MicSide.SOURCE)
                Orchestrator.instance.switchToRecorder(MicSide.TARGET)
            else {
                if (isRecording) {
                    isRecording = false
                    Orchestrator.instance.stopDialogRecording(MicSide.TARGET)
                    updateMicUI()
                } else {
                    isRecording = true
                    Orchestrator.instance.switchToRecorder(MicSide.TARGET)
                    updateMicUI()
                }
            }
        }

        binding.doneButton.setOnClickListener {
            Orchestrator.instance.stopDialogTranslation()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.leftPartialFlow.collect {
                    val wasNull = adapter.leftPartial == null
                    adapter.leftPartial = DialogTranslationViewModel.DialogTranslationLeftMessage(it.first, it.second)
                    if (wasNull) {
                        adapter.notifyItemInserted(2 + adapter.history.size)
                        binding.recyclerView.invalidateItemDecorations()
                    } else {
                        adapter.notifyItemChanged(2 + adapter.history.size)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rightPartialFlow.collect {
                    val wasNull = adapter.rightPartial == null
                    adapter.rightPartial = DialogTranslationViewModel.DialogTranslationRightMessage(it.first, it.second)
                    val position = 2 + adapter.history.size + (if (adapter.leftPartial != null) 1 else 0)
                    if (wasNull) {
                        adapter.notifyItemInserted(position)
                        binding.recyclerView.invalidateItemDecorations()
                    } else {
                        adapter.notifyItemChanged(position)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.leftResultFlow.collect {
                    val partialPosition = 2 + adapter.history.size
                    val hadLeftPartial = adapter.leftPartial != null
                    adapter.leftPartial = null
                    adapter.history = viewModel.messages
                    if (hadLeftPartial) {
                        adapter.notifyItemRemoved(partialPosition)
                    }
                    adapter.notifyItemInserted(partialPosition)
                    binding.recyclerView.invalidateItemDecorations()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rightResultFlow.collect {
                    val partialPosition = 2 + adapter.history.size + (if (adapter.leftPartial != null) 1 else 0)
                    val hadRightPartial = adapter.rightPartial != null
                    adapter.rightPartial = null
                    adapter.history = viewModel.messages
                    if (hadRightPartial) {
                        adapter.notifyItemRemoved(partialPosition)
                    }
                    adapter.notifyItemInserted(2 + adapter.history.size - 1)
                    binding.recyclerView.invalidateItemDecorations()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.micSide.collect { side ->
                    micSide = side
                    isRecording = side == DialogTranslationCapability.MicSide.TARGET
                    updateMicUI()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stop.collect {
                    findNavController().popBackStack()
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            Orchestrator.instance.stopDialogTranslation()
        }

        return binding.root
    }

    private fun updateMicUI() {
        when (micSide) {
            MicSide.SOURCE -> {
                isRecording = false
                binding.buttonTextView.text = requireContext().resources.getString(R.string.start)
                binding.waveView.visibility = View.INVISIBLE
                binding.micImageView.visibility = View.VISIBLE
            }
            MicSide.TARGET -> {
                if (isRecording) {
                    binding.buttonTextView.text = requireContext().resources.getString(R.string.pause)
                    binding.waveView.visibility = View.VISIBLE
                    binding.micImageView.visibility = View.INVISIBLE
                } else {
                    binding.buttonTextView.text = requireContext().resources.getString(R.string.start)
                    binding.waveView.visibility = View.INVISIBLE
                    binding.micImageView.visibility = View.VISIBLE
                }
            }
        }
    }

    class Adapter(private val sourceLanguage: String, private val targetLanguage: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        companion object {
            const val RIGHT_EXAMPLE = 0
            const val LEFT_EXAMPLE = 1
            const val LEFT_MESSAGE = 2
            const val RIGHT_MESSAGE = 3
        }

        var history = listOf<DialogTranslationViewModel.DialogTranslationMessage>()
        var leftPartial: DialogTranslationViewModel.DialogTranslationLeftMessage? = null
        var rightPartial: DialogTranslationViewModel.DialogTranslationRightMessage? = null

        private fun getPartialCount(): Int {
            var count = 0
            if (leftPartial != null) count++
            if (rightPartial != null) count++
            return count
        }

        class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.textView)
        }

        class RightExampleViewHoler(view: View) : RecyclerView.ViewHolder(view) {
            val languageTextView: TextView = view.findViewById(R.id.languageTextView)
        }

        class LeftExampleViewHoler(view: View) : RecyclerView.ViewHolder(view) {
            val sourceLanguageTextView: TextView = view.findViewById(R.id.sourceLanguageTextView)
            val targetLanguageTextView: TextView = view.findViewById(R.id.targetLanguageTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            when (viewType) {
                RIGHT_EXAMPLE -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_translation_setting_right_example_item, parent, false)
                    return RightExampleViewHoler(view)
                }

                LEFT_EXAMPLE -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_translation_setting_left_example_item, parent, false)
                    return LeftExampleViewHoler(view)
                }

                LEFT_MESSAGE -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_translation_left_item, parent, false)
                    return MessageViewHolder(view)
                }

                else -> {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_translation_right_item, parent, false)
                    return MessageViewHolder(view)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val viewType = getItemViewType(position)

            when (viewType) {
                RIGHT_EXAMPLE -> {
                    val holder = holder as RightExampleViewHoler
                    holder.languageTextView.text = targetLanguage
                }
                LEFT_EXAMPLE -> {
                    val holder = holder as LeftExampleViewHoler
                    holder.sourceLanguageTextView.text = sourceLanguage
                    holder.targetLanguageTextView.text = targetLanguage
                }
                LEFT_MESSAGE, RIGHT_MESSAGE -> {
                    val historyEndPosition = 2 + history.size
                    if (position < historyEndPosition) {
                        val message = history[position - 2]
                        message.onBindViewHolder(holder)
                    } else {
                        val partialIndex = position - historyEndPosition
                        when {
                            partialIndex == 0 && leftPartial != null -> leftPartial?.onBindViewHolder(holder)
                            partialIndex == 0 && leftPartial == null && rightPartial != null -> rightPartial?.onBindViewHolder(holder)
                            partialIndex == 1 && rightPartial != null -> rightPartial?.onBindViewHolder(holder)
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return 2 + history.size + getPartialCount()
        }

        override fun getItemViewType(position: Int): Int {
            return when {
                position == 0 -> RIGHT_EXAMPLE
                position == 1 -> LEFT_EXAMPLE
                position - 2 < history.size -> history[position - 2].viewType
                else -> {
                    val historyEndPosition = 2 + history.size
                    val partialIndex = position - historyEndPosition
                    when {
                        partialIndex == 0 && leftPartial != null -> LEFT_MESSAGE
                        partialIndex == 0 && leftPartial == null && rightPartial != null -> RIGHT_MESSAGE
                        partialIndex == 1 && rightPartial != null -> RIGHT_MESSAGE
                        else -> LEFT_MESSAGE
                    }
                }
            }
        }
    }
}