package com.konami.ailens.signup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.api.SendOTPRequest
import com.konami.ailens.api.SessionManager
import com.konami.ailens.api.VerifyOTPRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class VerifyOTPViewModel : ViewModel() {
    private val _verifyOTPSuccessEvent = MutableSharedFlow<Unit>()
    val verifyOTPSuccessEvent: SharedFlow<Unit> = _verifyOTPSuccessEvent.asSharedFlow()

    private val _verifyOTPFailureEvent = MutableSharedFlow<String>()
    val verifyOTPFailureEvent: SharedFlow<String> = _verifyOTPFailureEvent.asSharedFlow()

    private val _verificationEmailSuccessEvent = MutableSharedFlow<Unit>()
    val verificationEmailSuccessEvent: SharedFlow<Unit> = _verificationEmailSuccessEvent.asSharedFlow()

    private val _verificationEmailFailedEvent = MutableSharedFlow<String>()
    val verificationEmailFailedEvent: SharedFlow<String> = _verificationEmailFailedEvent.asSharedFlow()

    fun verifyOTP(email: String, otp: String) {
        viewModelScope.launch {
            try {
                val request = VerifyOTPRequest(email, otp)
                val result = request.execute()

                SessionManager.saveTokens(
                    access = result.accessToken,
                    refresh = result.refreshToken,
                    expiresAt = result.expiresAt
                )

                _verifyOTPSuccessEvent.emit(Unit)
            } catch (e: Exception) {
                Log.e("VerifyOTPViewModel", "verifyOTP failed: ${e.message}")
                _verifyOTPFailureEvent.emit(e.message ?: "Unknown error")
            }
        }
    }

    fun sendVerificationEmail(email: String) {
        viewModelScope.launch {
            try {
                val request = SendOTPRequest(email)
                request.execute()
                _verificationEmailSuccessEvent.emit(Unit)
            } catch (e: Exception) {
                Log.e("VerifyOTPViewModel", "sendVerificationEmail failed: ${e.message}")
                _verificationEmailFailedEvent.emit(e.message ?: "Unknown error")
            }
        }
    }
}
