package com.example.smart_solar_mgt_app.ui.auth

sealed class RegisterUiState {
    data object Idle : RegisterUiState()
    data object Loading : RegisterUiState()
    data class FieldErrors(val errors: Map<RegisterField, String>) : RegisterUiState()
    data class FormError(val message: String) : RegisterUiState()

    /** Account created as PENDING_APPROVAL - no session was started. */
    data object PendingActivation : RegisterUiState()
}
