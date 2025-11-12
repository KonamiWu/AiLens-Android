package com.konami.ailens.translation.interpretation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.libraries.navigation.internal.acd.ho
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentInterpretationBinding
import com.konami.ailens.navigation.VerticalDivider
import com.konami.ailens.orchestrator.Orchestrator
import kotlinx.coroutines.launch

class InterpretationFragment: Fragment() {
    private lateinit var binding: FragmentInterpretationBinding
    private lateinit var recyclerView: RecyclerView
    private val autoScrollNumber = 50f
    private var autoScrollThreshold: Float = 0f
    private val adapter = InterpretationAdapter()
    private val viewModel: InterpretationViewModel by viewModels()

    private val bilingual = Orchestrator.instance.bilingual

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        autoScrollThreshold = inflater.context.resources.displayMetrics.density * autoScrollNumber
        binding = FragmentInterpretationBinding.inflate(inflater, container, false)
        recyclerView = binding.recyclerView
        setupUI(inflater.context)

        bind()

        binding.waveView.start()
        return binding.root
    }

    private fun setupUI(context: Context) {
        val layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        recyclerView.layoutManager = layoutManager
        val divider = VerticalDivider(context.resources.getDimension(R.dimen.interpretation_item_spacing).toInt(), Color.TRANSPARENT)
        recyclerView.addItemDecoration(divider)
        recyclerView.adapter = adapter
        adapter.bilingual = bilingual

        binding.doneButton.setOnClickListener {
            viewModel.stop()
        }

        binding.sourceTextView.text = Orchestrator.instance.interpretationSourceLanguage.title
        binding.targetTextView.text = Orchestrator.instance.interpretationTargetLanguage.title
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun bind() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.partialFlow.collect {
                        adapter.updatePartial(viewModel.partial)
                        adapter.notifyItemChanged(viewModel.history.size)
                        scrollToBottomIfNeeded()
                    }
                }

                launch {
                    viewModel.resultFlow.collect {
                        adapter.updatePartial(viewModel.partial)
                        adapter.updateHistory(viewModel.history)
                        adapter.notifyDataSetChanged()
                        scrollToBottomIfNeeded()
                    }
                }

                launch {
                    viewModel.isStop.collect {
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private fun scrollToBottomIfNeeded() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

        if (!isNearBottom()) {
            return
        }

        val itemCount = adapter.itemCount
        if (itemCount > 0) {
            recyclerView.smoothScrollToPosition(itemCount - 1)
        }
    }

    private fun isNearBottom(): Boolean {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return false

        val contentHeight = recyclerView.computeVerticalScrollRange()
        val viewportHeight = recyclerView.height
        val currentScrollOffset = recyclerView.computeVerticalScrollOffset()

        if (contentHeight <= viewportHeight) {
            return true
        }

        val thresholdPx = autoScrollThreshold * resources.displayMetrics.density
        val distanceFromBottom = contentHeight - viewportHeight - currentScrollOffset

        return distanceFromBottom <= thresholdPx
    }

    class InterpretationAdapter: RecyclerView.Adapter<InterpretationAdapter.ViewHolder>() {
        class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
            val sourceTextView: TextView = view.findViewById(R.id.sourceTextView)
            val targetTextView: TextView = view.findViewById(R.id.targetTextView)
        }

        private var history = listOf<Pair<String, String>>()
        private var partial: Pair<String, String>? = null

        var bilingual = true

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InterpretationAdapter.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.interpretation_item, parent, false)
            val holder = ViewHolder(view)
            if (!bilingual)
                holder.sourceTextView.visibility = View.GONE

            return holder
        }

        override fun onBindViewHolder(holder: InterpretationAdapter.ViewHolder, position: Int) {
            if (position < history.size) {
                if (bilingual)
                    holder.sourceTextView.text = history[position].first
                holder.targetTextView.text = history[position].second
            } else {
                if (bilingual)
                    holder.sourceTextView.text = partial?.first ?: ""
                holder.targetTextView.text = partial?.second ?: ""
            }
        }

        override fun getItemCount(): Int = history.size + 1

        fun updatePartial(partial: Pair<String, String>?) {
            this.partial = partial
        }

        fun updateHistory(history: List<Pair<String, String>>) {
            this.history = history
        }
    }
}