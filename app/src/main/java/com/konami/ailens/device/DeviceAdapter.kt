package com.konami.ailens.device

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.R
import com.konami.ailens.ble.AiLens

class DeviceAdapter(
    private val onItemClick: (AiLens) -> Unit
) : ListAdapter<DeviceAdapter.BLEListItem, RecyclerView.ViewHolder>(DIFF) {

    sealed class BLEListItem {
        data class Header(val title: String) : BLEListItem()
        data class Device(val glasses: AiLens) : BLEListItem()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1

        val DIFF = object : DiffUtil.ItemCallback<BLEListItem>() {
            override fun areItemsTheSame(old: BLEListItem, new: BLEListItem): Boolean =
                when {
                    old is BLEListItem.Header && new is BLEListItem.Header -> old.title == new.title
                    old is BLEListItem.Device && new is BLEListItem.Device ->
                        old.glasses.device.address == new.glasses.device.address
                    else -> false
                }
            @SuppressLint("MissingPermission")
            override fun areContentsTheSame(old: BLEListItem, new: BLEListItem): Boolean =
                when {
                    old is BLEListItem.Header && new is BLEListItem.Header -> old.title == new.title
                    old is BLEListItem.Device && new is BLEListItem.Device ->
                        old.glasses.state == new.glasses.state &&
                                old.glasses.device.name == new.glasses.device.name &&
                                old.glasses.device.address == new.glasses.device.address
                    else -> false
                }
        }
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is BLEListItem.Header -> TYPE_HEADER
        is BLEListItem.Device -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.device_section_item, parent, false)
            BLESectionViewHolder(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
            BLEViewHolder(v, onItemClick)
        }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is BLEListItem.Header -> (holder as BLESectionViewHolder).nameTextView.text = item.title
            is BLEListItem.Device -> (holder as BLEViewHolder).bind(item.glasses, position == 1)
        }
    }

    class BLESectionViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val nameTextView: TextView = v.findViewById(R.id.nameTextView)
    }

    @SuppressLint("MissingPermission")
    class BLEViewHolder(v: View, val onItemClick: (AiLens) -> Unit) : RecyclerView.ViewHolder(v) {
        private val nameTextView: TextView = v.findViewById(R.id.nameTextView)
        private val imageView: ImageView = v.findViewById(R.id.imageView)
        private val macTextView: TextView = v.findViewById(R.id.macTextView)
        private val statusTextView: TextView = v.findViewById(R.id.stateTextView)
        private val backgroundView: ConstraintLayout = v.findViewById(R.id.backgroundView)

        fun bind(glasses: AiLens, isLast: Boolean) {
            nameTextView.text = glasses.device.name
            macTextView.text = glasses.device.address
            statusTextView.text = glasses.state.value
            imageView.setImageResource(if (isLast) R.drawable.connected else R.drawable.disconnected)
            backgroundView.setOnClickListener { onItemClick(glasses) }
        }
    }
}