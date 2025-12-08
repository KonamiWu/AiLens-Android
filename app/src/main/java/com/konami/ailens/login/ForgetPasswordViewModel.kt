package com.konami.ailens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.api.ResetPasswordRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ForgetPasswordViewModel : ViewModel() {
    private val _verificationEmailSuccessEvent = MutableSharedFlow<Unit>()
    val verificationEmailSuccessEvent: SharedFlow<Unit> = _verificationEmailSuccessEvent.asSharedFlow()

    private val _verificationEmailFailedEvent = MutableSharedFlow<String>()
    val verificationEmailFailedEvent: SharedFlow<String> = _verificationEmailFailedEvent.asSharedFlow()

    fun isValidEmail(email: String): Boolean {
        val emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}".toRegex()
        return emailRegex.matches(email)
    }

    fun sendVerificationEmail(email: String) {
        viewModelScope.launch {
            try {
                val request = ResetPasswordRequest(email)
                request.execute()
                _verificationEmailSuccessEvent.emit(Unit)
            } catch (e: Exception) {
                _verificationEmailFailedEvent.emit(e.message ?: "Unknown error")
            }
        }
    }
}
