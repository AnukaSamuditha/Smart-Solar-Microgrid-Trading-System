package com.example.smart_solar_mgt_app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.security.LoginResult
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(private val securityManager: SecurityManager) : ViewModel() {

    private val _state = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val state: LiveData<LoginUiState> = _state

    fun onLoginClicked(nicInput: String, passwordInput: String) {
        val nic = nicInput.trim()
        if (nic.isEmpty()) {
            _state.value = LoginUiState.FieldError(LoginField.NIC, "NIC or email is required")
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
                LoginResult.InvalidCredentials -> LoginUiState.FormError("Invalid NIC/email or password")
                is LoginResult.AccountNotActive -> LoginUiState.FormError(messageFor(result.status))
                is LoginResult.Error -> LoginUiState.FormError(result.message)
                LoginResult.PendingApproval -> LoginUiState.PendingApproval
                LoginResult.AccountCreationDenied -> LoginUiState.AccountCreationDenied
                LoginResult.PasswordNotSet -> LoginUiState.PasswordNotSet
            }
        }
    }

    private fun messageFor(status: AccountStatus): String = when (status) {
        AccountStatus.PENDING_APPROVAL -> "Your account is awaiting activation. Please try again later."
        AccountStatus.DEACTIVATED -> "Your account has been deactivated."
        AccountStatus.SUSPENDED -> "Your account is suspended. Contact support."
        AccountStatus.REJECTED -> "Your account registration was rejected. Contact support."
        AccountStatus.ACTIVE, AccountStatus.DEACTIVATION_REQUESTED ->
            "Your account cannot log in right now. Contact support." // shouldn't happen - AccountLoginPolicy allows both
    }
}
