package com.konami.ailens.setting.item

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.konami.ailens.databinding.SettingSwitchItemBinding

data class SwitchSettingItem(
    val icon: Drawable?,
    val title: String,
    var isOn: Boolean,
    var sliderValue: Int? = null,
    val onToggle: (Boolean) -> Unit,
    val onSliderChange: ((Int) -> Unit)? = null
) : SettingItem {

    override val viewType: Int = TYPE_SWITCH

    override fun createViewHolder(parent: ViewGroup): SettingViewHolder {
        val binding = SettingSwitchItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SwitchViewHolder(binding)
    }

    override fun bind(holder: SettingViewHolder) {
        (holder as SwitchViewHolder).bind(this)
    }

    class SwitchViewHolder(
        private val binding: SettingSwitchItemBinding
    ) : SettingViewHolder(binding.root) {

        private var currentItem: SwitchSettingItem? = null

        init {
            binding.switchToggle.setOnCheckedChangeListener { _, isChecked ->
                currentItem?.let { item ->
                    item.isOn = isChecked
                    updateSliderVisibility(isChecked)
                    item.onToggle(isChecked)
                }
            }

            binding.slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        currentItem?.let { item ->
                            item.sliderValue = progress
                            item.onSliderChange?.invoke(progress)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        override fun bind(item: SettingItem) {
            currentItem = item as SwitchSettingItem

            binding.imageView.setImageDrawable(item.icon)
            binding.titleTextView.text = item.title
            binding.switchToggle.isChecked = item.isOn

            item.sliderValue?.let { value ->
                binding.slider.progress = value
                updateSliderVisibility(item.isOn)
            } ?: run {
                binding.slider.visibility = View.GONE
            }
        }

        private fun updateSliderVisibility(isOn: Boolean) {
            currentItem?.sliderValue?.let {
                binding.slider.visibility = if (isOn) View.VISIBLE else View.GONE
            }
        }
    }

    companion object {
        const val TYPE_SWITCH = 2
    }
}
