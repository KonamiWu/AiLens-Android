package com.konami.ailens.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.api.SignUpRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SignUpViewModel : ViewModel() {
    private val _signUpSuccessEvent = MutableSharedFlow<Unit>()
    val signUpSuccessEvent: SharedFlow<Unit> = _signUpSuccessEvent.asSharedFlow()

    private val _signUpFailedEvent = MutableSharedFlow<String>()
    val signUpFailedEvent: SharedFlow<String> = _signUpFailedEvent.asSharedFlow()

    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            try {
                val request = SignUpRequest(email, password, displayName)
                request.execute()
                _signUpSuccessEvent.emit(Unit)
            } catch (e: Exception) {
                _signUpFailedEvent.emit(e.message ?: "Unknown error")
            }
        }
    }
}
