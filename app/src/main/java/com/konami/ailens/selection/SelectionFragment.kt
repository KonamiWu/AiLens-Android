package com.konami.ailens.selection

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentSelectionBinding
import com.konami.ailens.navigation.VerticalDivider
import com.konami.ailens.resolveAttrColor

class SelectionFragment<T : SelectionItem> : BottomSheetDialogFragment() {
    private lateinit var binding: FragmentSelectionBinding
    private var items: List<T> = emptyList()
    private var selectedItem: T? = null
    private var topMargin: Float = 0f
    private var bottomInset: Int = 0
    private var onItemSelected: ((T) -> Unit)? = null

    companion object {
        fun <T : SelectionItem> newInstance(
            items: List<T>,
            currentItem: T?,
            onItemSelected: (T) -> Unit
        ): SelectionFragment<T> {
            return SelectionFragment<T>().apply {
                this.items = items
                this.selectedItem = currentItem
                this.onItemSelected = onItemSelected
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(android.R.color.transparent)

                ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
                    v.setPadding(
                        v.paddingLeft,
                        v.paddingTop,
                        v.paddingRight,
                        0
                    )
                    insets
                }

                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.peekHeight = resources.displayMetrics.heightPixels

                val layoutParams = sheet.layoutParams
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                sheet.layoutParams = layoutParams
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSelectionBinding.inflate(inflater, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val topInset = systemBarsInsets.top
            bottomInset = systemBarsInsets.bottom
            val spacing = resources.getDimension(R.dimen.language_selection_top_margin_top)
            topMargin = spacing + topInset

            view.post {
                adjustRecyclerViewHeight()
            }

            insets
        }

        binding.root.setOnClickListener {
            dismiss()
        }

        val adapter = Adapter(requireContext(), items, selectedItem) { item ->
            onItemSelected?.invoke(item)
            dismiss()
        }

        val divider = VerticalDivider(inflater.context.resources.displayMetrics.density.toInt(), inflater.context.resolveAttrColor(R.attr.appBorderDarkGray))
        binding.recycleView.addItemDecoration(divider)
        binding.recycleView.adapter = adapter

        return binding.root
    }

    private fun adjustRecyclerViewHeight() {
        val itemHeight = resources.getDimension(R.dimen.language_selection_item_height)
        val topHeight = resources.getDimension(R.dimen.language_selection_top_height)
        val itemCount = items.size
        val contentHeight = itemHeight * itemCount + bottomInset

        val maxHeight = (binding.root.height - topMargin - topHeight - bottomInset).toInt()

        val layoutParams = binding.recycleView.layoutParams
        layoutParams.height = minOf(contentHeight.toInt(), maxHeight)
        binding.recycleView.layoutParams = layoutParams
    }

    class Adapter<T : SelectionItem>(
        private val context: Context,
        private val items: List<T>,
        private var selectedItem: T?,
        private val onClick: (T) -> Unit
    ) : RecyclerView.Adapter<Adapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.textView)
            val checkImageView: ImageView = view.findViewById(R.id.checkImageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.language_selection_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textView.text = item.displayText

            if (item.id == selectedItem?.id) {
                holder.checkImageView.visibility = View.VISIBLE
                holder.textView.setTextColor(context.resolveAttrColor(R.attr.appPrimary))
            } else {
                holder.checkImageView.visibility = View.INVISIBLE
                holder.textView.setTextColor(context.resolveAttrColor(R.attr.appTextPrimary))
            }

            holder.itemView.setOnClickListener {
                onClick(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
