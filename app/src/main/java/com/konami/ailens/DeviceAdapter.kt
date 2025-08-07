package com.konami.ailens

import AiLens
import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

class DeviceAdapter(private val onItemClick: (AiLens) -> Unit): RecyclerView.Adapter<ViewHolder>() {
    sealed class BLEListItem {
        data class Header(val title: String) : BLEListItem()
        data class Device(val glasses: AiLens) : BLEListItem()
    }

    @SuppressLint("MissingPermission")
    class BLEViewHolder(itemView: View, val onItemClick: (AiLens) -> Unit): ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val macTextView: TextView = itemView.findViewById(R.id.macTextView)
        val statusTextView: TextView = itemView.findViewById(R.id.stateTextView)
        val backgroundView: ConstraintLayout = itemView.findViewById(R.id.backgroundView)

        fun bindLast(glasses: AiLens) {
            nameTextView.text = glasses.device.name
            macTextView.text = glasses.device.address
            statusTextView.text = glasses.state.state.value
            imageView.setImageResource(R.drawable.connected)
            backgroundView.setOnClickListener {
                onItemClick(glasses)
            }
        }

        fun bindNew(glasses: AiLens) {
            Log.e("TAG", "glasses.device.name = ${glasses.device.name}")
            nameTextView.text = glasses.device.name
            macTextView.text = glasses.device.address
            statusTextView.text = glasses.state.state.value
            imageView.setImageResource(R.drawable.disconnected)
            backgroundView.setOnClickListener {
                onItemClick(glasses)
            }
        }
    }

    @SuppressLint("MissingPermission")
    class BLESectionViewHolder(itemView: View): ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
    }

    companion object {
        private const val TAG = "BLEListAdapter"
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val items = mutableListOf<BLEListItem>()
    private var lastGlasses: AiLens? = null
    private var availableGlasses = listOf<AiLens>()
    init {
        items.add(BLEListItem.Header("My Glasses"))
        lastGlasses?.let { items.add(BLEListItem.Device(it)) }

        items.add(BLEListItem.Header("Available Glasses"))
        items.addAll(availableGlasses.map { BLEListItem.Device(it) })

        notifyDataSetChanged()
    }
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is BLEListItem.Header -> TYPE_HEADER
            is BLEListItem.Device -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.device_section_item, parent, false)
            BLESectionViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.device_item, parent, false)
            BLEViewHolder(view, onItemClick)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BLEListItem.Header -> {
                val holder = (holder as BLESectionViewHolder)
                if (position == 0) {
                    holder.nameTextView.text = "My Glasses"
                } else {
                    holder.nameTextView.text = "Available Glasses"
                }

            }
            is BLEListItem.Device -> {
                val holder = (holder as BLEViewHolder)
                if (position == 1) {
                    holder.bindLast(item.glasses)
                } else {
                    holder.bindNew(item.glasses)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun update(lastGlasses: AiLens?) {
        this.lastGlasses = lastGlasses
        items.clear()
        items.add(BLEListItem.Header("My Glasses"))
        lastGlasses?.let { items.add(BLEListItem.Device(it)) }

        items.add(BLEListItem.Header("Available Glasses"))
        items.addAll(availableGlasses.map { BLEListItem.Device(it) })
        notifyDataSetChanged()
    }

    fun update(availableGlasses: List<AiLens>) {
        items.clear()
        this.availableGlasses = availableGlasses
        items.add(BLEListItem.Header("My Glasses"))
        lastGlasses?.let { items.add(BLEListItem.Device(it)) }

        items.add(BLEListItem.Header("Available Glasses"))
        items.addAll(availableGlasses.map { BLEListItem.Device(it) })
        notifyDataSetChanged()
    }
}