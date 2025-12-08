package com.konami.ailens.setting

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.konami.ailens.R

class PowerSavingSettingListItem(private val navController: NavController): SettingListItem {
    override val icon: Drawable?
        get() = ContextCompat.getDrawable(navController.context, R.drawable.ic_setting_power_saving)
    override val title: String
        get() = navController.context.getString(R.string.setting_power_saving)

    override fun execute() {

    }
}