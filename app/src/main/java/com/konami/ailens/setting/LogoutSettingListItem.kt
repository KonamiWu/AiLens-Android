package com.konami.ailens.setting

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.api.SessionManager
import com.konami.ailens.ble.BLEService
import com.konami.ailens.login.LoginActivity

class LogoutSettingListItem(private val activity: Activity): SettingListItem {
    override val icon: Drawable?
        get() = ContextCompat.getDrawable(activity, R.drawable.ic_setting_about)
    override val title: String
        get() = activity.getString(R.string.setting_logout)

    override fun execute() {
        BLEService.instance.clearSession()
        SessionManager.logout()
        SharedPrefs.instance.cleanUp()
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val options = android.app.ActivityOptions.makeCustomAnimation(
            activity,
            R.anim.flip_in,
            R.anim.flip_out
        )

        activity.startActivity(intent, options.toBundle())
    }
}