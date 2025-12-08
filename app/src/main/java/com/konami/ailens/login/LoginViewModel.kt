package com.konami.ailens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.konami.ailens.SharedPrefs
import com.konami.ailens.api.SessionManager
import com.konami.ailens.api.SignInRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    private val _loginFailedEvent = MutableSharedFlow<String>()
    val loginFailedEvent: SharedFlow<String> = _loginFailedEvent.asSharedFlow()

    private val _showAddDeviceEvent = MutableSharedFlow<Unit>()
    val showAddDeviceEvent: SharedFlow<Unit> = _showAddDeviceEvent.asSharedFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val request = SignInRequest(email, password)
                val result = request.execute()

                SessionManager.saveTokens(
                    access = result.user.session.accessToken,
                    refresh = result.user.session.refreshToken,
                    expiresAt = result.user.session.expiresAt
                )
                _showAddDeviceEvent.emit(Unit)
            } catch (e: Exception) {
                _loginFailedEvent.emit(e.message ?: "Unknown error")
            }
        }
    }

    fun isValidEmail(email: String): Boolean {
        val emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}".toRegex()
        return emailRegex.matches(email)
    }
}
