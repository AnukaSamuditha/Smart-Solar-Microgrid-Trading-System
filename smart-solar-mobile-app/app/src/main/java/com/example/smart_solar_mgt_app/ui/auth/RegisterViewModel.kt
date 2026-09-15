package com.example.smart_solar_mgt_app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.AuthRepository
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _state = MutableLiveData<RegisterUiState>(RegisterUiState.Idle)
    val state: LiveData<RegisterUiState> = _state

    fun onRegisterClicked(input: RegistrationValidator.Input) {
        val errors = RegistrationValidator.validate(input)
        if (errors.isNotEmpty()) {
            _state.value = RegisterUiState.FieldErrors(errors)
            return
        }

        _state.value = RegisterUiState.Loading
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { register(input) }
            _state.value = outcome
        }
    }

    private fun register(input: RegistrationValidator.Input): RegisterUiState {
        val nic = input.nic.trim()
        val email = input.email.trim()

        if (authRepository.findUserByNic(nic) != null) {
            return RegisterUiState.FieldErrors(mapOf(RegisterField.NIC to "This NIC is already registered"))
        }
        if (authRepository.findUserByEmail(email) != null) {
            return RegisterUiState.FieldErrors(mapOf(RegisterField.EMAIL to "This email is already registered"))
        }

        val hashedPassword = securityManager.registerHash(input.password.toCharArray())
        val newUser = User(
            nic = nic,
            name = input.fullName.trim(),
            email = email,
            phone = input.phone.trim(),
            address = input.address.trim(),
            passwordHash = hashedPassword,
            role = Role.PROSUMER,
            accountStatus = AccountStatus.PENDING_APPROVAL
        )

        return when (val result = authRepository.createUser(newUser)) {
            // No auto-login: a PENDING_APPROVAL account has no business holding an authenticated
            // session yet - the user goes back to Login and waits to be activated.
            is AppResult.Success -> RegisterUiState.PendingActivation
            is AppResult.Failure -> when (result.error) {
                is AppError.UniqueConstraintViolation ->
                    RegisterUiState.FieldErrors(mapOf(RegisterField.NIC to "Already registered"))
                else -> RegisterUiState.FormError("Registration failed. Please try again.")
            }
        }
    }
}
