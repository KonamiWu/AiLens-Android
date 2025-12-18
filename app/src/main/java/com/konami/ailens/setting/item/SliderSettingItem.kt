package com.konami.ailens.setting.item

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import com.konami.ailens.databinding.SettingSliderItemBinding

data class SliderSettingItem(
    val icon: Drawable?,
    val title: String,
    var value: Int,
    val min: Int = 0,
    val max: Int = 100,
    val step: Int = 1,
    val valueFormatter: (Int) -> String = { "$it" },
    val onValueChange: (Int) -> Unit
) : SettingItem {

    override val viewType: Int = TYPE_SLIDER

    override fun createViewHolder(parent: ViewGroup): SettingViewHolder {
        val binding = SettingSliderItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SliderViewHolder(binding)
    }

    override fun bind(holder: SettingViewHolder) {
        (holder as SliderViewHolder).bind(this)
    }

    class SliderViewHolder(
        private val binding: SettingSliderItemBinding
    ) : SettingViewHolder(binding.root) {

        private var currentItem: SliderSettingItem? = null

        init {
            binding.slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentItem?.let { item ->
                            item.value = progress
                            updateValueText(item, progress)
                            item.onValueChange(progress)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        override fun bind(item: SettingItem) {
            currentItem = item as SliderSettingItem

            binding.imageView.setImageDrawable(item.icon)
            binding.titleTextView.text = item.title

            binding.slider.max = item.max
            binding.slider.progress = item.value

            updateValueText(item, item.value)
        }

        private fun updateValueText(item: SliderSettingItem, value: Int) {
            binding.valueTextView.text = item.valueFormatter(value)
        }
    }

    companion object {
        const val TYPE_SLIDER = 3
    }
}
