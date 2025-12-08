package com.konami.ailens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.api.UpdatePasswordRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ResetPasswordViewModel : ViewModel() {
    private val _updatePasswordSuccessEvent = MutableSharedFlow<Unit>()
    val updatePasswordSuccessEvent: SharedFlow<Unit> = _updatePasswordSuccessEvent.asSharedFlow()

    private val _updatePasswordFailureEvent = MutableSharedFlow<String>()
    val updatePasswordFailureEvent: SharedFlow<String> = _updatePasswordFailureEvent.asSharedFlow()

    fun updatePassword(password: String) {
        viewModelScope.launch {
            try {
                val request = UpdatePasswordRequest(password)
                request.execute()
                _updatePasswordSuccessEvent.emit(Unit)
            } catch (e: Exception) {
                _updatePasswordFailureEvent.emit(e.message ?: "Unknown error")
            }
        }
    }
}
