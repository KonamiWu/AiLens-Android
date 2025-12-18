package com.konami.ailens.setting.item

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import com.konami.ailens.databinding.SettingListItemBinding

data class SimpleSettingItem(
    val icon: Drawable?,
    val title: String,
    val onClick: () -> Unit
) : SettingItem {

    override val viewType: Int = TYPE_SIMPLE

    override fun createViewHolder(parent: ViewGroup): SettingViewHolder {
        val binding = SettingListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SimpleViewHolder(binding)
    }

    override fun bind(holder: SettingViewHolder) {
        (holder as SimpleViewHolder).bind(this)
    }

    class SimpleViewHolder(
        private val binding: SettingListItemBinding
    ) : SettingViewHolder(binding.root) {

        private var currentItem: SimpleSettingItem? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.onClick?.invoke()
            }
        }

        override fun bind(item: SettingItem) {
            currentItem = item as SimpleSettingItem
            binding.imageView.setImageDrawable(item.icon)
            binding.titleTextView.text = item.title
        }
    }

    companion object {
        const val TYPE_SIMPLE = 1
    }
}
