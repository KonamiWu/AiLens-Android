package com.konami.ailens.login

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.konami.ailens.MainActivity
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.api.SessionManager
import com.konami.ailens.databinding.ActivityLoginBinding
import com.konami.ailens.device.AddDeviceActivity

class LoginActivity: AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is logged in
        val userId = SessionManager.getUserId()
        if (userId != null) {
            // User is logged in, check device
            navigateToAddDevice()
            return
        }

        // Not logged in, show login screen
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun navigateToAddDevice() {
        val intent = Intent(this, AddDeviceActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val options = android.app.ActivityOptions.makeCustomAnimation(
            this,
            R.anim.flip_in,
            R.anim.flip_out
        )

        startActivity(intent, options.toBundle())
        finish()
    }

}