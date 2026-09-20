package com.example.smart_solar_mgt_app.ui.auth

enum class ResetPasswordField { CODE, PASSWORD, CONFIRM_PASSWORD }

sealed class ResetPasswordUiState {
    data object Idle : ResetPasswordUiState()
    data object Loading : ResetPasswordUiState()
    data class FieldError(val field: ResetPasswordField, val message: String) : ResetPasswordUiState()
    data class FormError(val message: String) : ResetPasswordUiState()
    data object Success : ResetPasswordUiState()
}
