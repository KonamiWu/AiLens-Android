package com.konami.ailens.device

import android.annotation.SuppressLint
import android.util.Log
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
import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.function.FunctionAdapter.FunctionViewHolder

class DeviceAdapter(
    private val onItemClick: (DeviceSession) -> Unit, private val onItemLongClick: (DeviceSession) -> Unit
) :  RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class BLEListItem {
        data class Header(val title: String) : BLEListItem()
        data class Device(val glasses: DeviceSession) : BLEListItem()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    var items = listOf<BLEListItem>()

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        is BLEListItem.Header -> TYPE_HEADER
        is BLEListItem.Device -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_HEADER) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.device_section_item, parent, false)
            BLESectionViewHolder(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
            BLEViewHolder(v, onItemClick, onItemLongClick)
        }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BLEListItem.Header -> (holder as BLESectionViewHolder).nameTextView.text = item.title
            is BLEListItem.Device -> (holder as BLEViewHolder).bind(item.glasses, position == 1)
        }
    }

    class BLESectionViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val nameTextView: TextView = v.findViewById(R.id.nameTextView)
    }

    @SuppressLint("MissingPermission")
    class BLEViewHolder(v: View, val onItemClick: (DeviceSession) -> Unit, val onItemLongClick: (DeviceSession) -> Unit) : RecyclerView.ViewHolder(v) {
        private val nameTextView: TextView = v.findViewById(R.id.nameTextView)
        private val imageView: ImageView = v.findViewById(R.id.imageView)
        private val macTextView: TextView = v.findViewById(R.id.macTextView)
        private val statusTextView: TextView = v.findViewById(R.id.stateTextView)
        private val backgroundView: ConstraintLayout = v.findViewById(R.id.backgroundView)

        fun bind(glasses: DeviceSession, isLast: Boolean) {
            nameTextView.text = glasses.device.name
            macTextView.text = glasses.device.address

            statusTextView.text = glasses.state.value.description
            imageView.setImageResource(if (isLast) R.drawable.connected else R.drawable.disconnected)
            backgroundView.setOnClickListener { onItemClick(glasses) }
            backgroundView.setOnLongClickListener {
                onItemLongClick(glasses)
                true
            }
        }
    }
}