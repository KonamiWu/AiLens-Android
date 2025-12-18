package com.konami.ailens.setting.item

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

interface SettingItem {
    val viewType: Int

    fun createViewHolder(parent: ViewGroup): SettingViewHolder
    fun bind(holder: SettingViewHolder)
}

abstract class SettingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun bind(item: SettingItem)
}
