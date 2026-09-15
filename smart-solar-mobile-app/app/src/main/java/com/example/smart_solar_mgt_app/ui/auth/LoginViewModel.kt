package com.example.smart_solar_mgt_app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.security.LoginResult
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(private val securityManager: SecurityManager) : ViewModel() {

    private val _state = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val state: LiveData<LoginUiState> = _state

    fun onLoginClicked(nicInput: String, passwordInput: String) {
        val nic = nicInput.trim()
        if (nic.isEmpty()) {
            _state.value = LoginUiState.FieldError(LoginField.NIC, "NIC is required")
            return
        }
        if (passwordInput.isEmpty()) {
            _state.value = LoginUiState.FieldError(LoginField.PASSWORD, "Password is required")
            return
        }

        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                securityManager.login(nic, passwordInput.toCharArray())
            }
            _state.value = when (result) {
                is LoginResult.Success -> LoginUiState.Success(result.session.role)
                LoginResult.InvalidCredentials -> LoginUiState.FormError("Invalid NIC or password")
                is LoginResult.AccountNotActive -> {
                    val status = result.status.name.lowercase().replace('_', ' ')
                    LoginUiState.FormError("Your account is $status. Contact support.")
                }
            }
        }
    }
}
