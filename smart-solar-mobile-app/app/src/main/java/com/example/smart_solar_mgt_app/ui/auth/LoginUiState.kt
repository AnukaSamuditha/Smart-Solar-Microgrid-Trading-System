package com.example.smart_solar_mgt_app.ui.auth

import com.example.smart_solar_mgt_app.domain.model.Role

enum class LoginField { NIC, PASSWORD }

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class FieldError(val field: LoginField, val message: String) : LoginUiState()
    data class FormError(val message: String) : LoginUiState()
    data class Success(val role: Role) : LoginUiState()
}
