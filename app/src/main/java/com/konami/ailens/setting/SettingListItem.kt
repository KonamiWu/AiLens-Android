package com.konami.ailens.setting

import android.content.Context
import android.graphics.drawable.Drawable

interface SettingListItem {
    val icon: Drawable?
    val title: String
    fun execute()
}