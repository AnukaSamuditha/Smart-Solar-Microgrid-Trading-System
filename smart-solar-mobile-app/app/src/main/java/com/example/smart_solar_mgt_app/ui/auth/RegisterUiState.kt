package com.example.smart_solar_mgt_app.ui.auth

sealed class RegisterUiState {
    data object Idle : RegisterUiState()
    data object Loading : RegisterUiState()
    data class FieldErrors(val errors: Map<RegisterField, String>) : RegisterUiState()
    data class FormError(val message: String) : RegisterUiState()

    /** Queued as PendingApproval (see LocalDbManager.registerProsumerLocally) - no session was started. */
    data class PendingActivation(val nic: String, val email: String) : RegisterUiState()
}
