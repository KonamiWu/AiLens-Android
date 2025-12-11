package com.konami.ailens.setting

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.konami.ailens.R

class AboutSettingListItem(private val navController: NavController): SettingListItem {
    override val icon: Drawable?
        get() = ContextCompat.getDrawable(navController.context, R.drawable.ic_setting_about)
    override val title: String
        get() = navController.context.getString(R.string.setting_about)

    override fun execute() {
        navController.navigate(R.id.action_SettingFragment_to_AboutFragment)
    }
}