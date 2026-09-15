package com.example.smart_solar_mgt_app.ui.prosumer.profile

import com.example.smart_solar_mgt_app.domain.model.User

sealed class EditProfileUiState {
    data object Loading : EditProfileUiState()
    data class Prefill(val user: User) : EditProfileUiState()
    data object Saving : EditProfileUiState()
    data class FieldErrors(val errors: Map<ProfileField, String>) : EditProfileUiState()
    data class FormError(val message: String) : EditProfileUiState()
    data object Success : EditProfileUiState()
}
