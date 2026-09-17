package com.example.smart_solar_mgt_app.ui.prosumer.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _state = MutableLiveData<EditProfileUiState>(EditProfileUiState.Loading)
    val state: LiveData<EditProfileUiState> = _state

    private var originalEmail: String = ""

    fun loadCurrentUser() {
        val nic = securityManager.currentSession()?.userId ?: return
        _state.value = EditProfileUiState.Loading
        viewModelScope.launch {
            val user = withContext(Dispatchers.IO) { authRepository.findUserByNic(nic) }
            _state.value = if (user != null) {
                originalEmail = user.email
                EditProfileUiState.Prefill(user)
            } else {
                EditProfileUiState.FormError("Could not load profile")
            }
        }
    }

    fun onSaveClicked(input: EditProfileValidator.Input) {
        val errors = EditProfileValidator.validate(input)
        if (errors.isNotEmpty()) {
            _state.value = EditProfileUiState.FieldErrors(errors)
            return
        }

        _state.value = EditProfileUiState.Saving
        viewModelScope.launch {
            val nic = securityManager.currentSession()?.userId ?: return@launch
            val outcome = withContext(Dispatchers.IO) { save(nic, input) }
            _state.value = outcome
        }
    }

    private fun save(nic: String, input: EditProfileValidator.Input): EditProfileUiState {
        val email = input.email.trim()
        if (email != originalEmail) {
            val existing = authRepository.findUserByEmail(email)
            if (existing != null && existing.nic != nic) {
                return EditProfileUiState.FieldErrors(mapOf(ProfileField.EMAIL to "This email is already registered"))
            }
        }

        return when (val result = authRepository.updateProfile(nic, input.fullName.trim(), email, input.phone.trim(), input.address.trim())) {
            is AppResult.Success -> EditProfileUiState.Success
            is AppResult.Failure -> when (result.error) {
                is AppError.UniqueConstraintViolation ->
                    EditProfileUiState.FieldErrors(mapOf(ProfileField.EMAIL to "This email is already registered"))
                else -> EditProfileUiState.FormError("Could not update profile. Please try again.")
            }
        }
    }
}
