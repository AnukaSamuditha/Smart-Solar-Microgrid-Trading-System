package com.example.smart_solar_mgt_app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.network.RemoteResetPasswordOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteResetPasswordRejection
import com.example.smart_solar_mgt_app.data.repository.RemoteAuthRepository
import com.example.smart_solar_mgt_app.util.FieldValidators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the mobile Reset Password screen - the code comes from the approval email
 * (ProsumerMobileApprovalEmailTemplate) rather than a clickable link, since this same
 * POST /api/v1/auth/accept-invitation call is what a web-invited staff/Prosumer account also
 * uses (see AuthEndpoints.AcceptInvitationAsync); this screen is just a different front end for it.
 */
class ResetPasswordViewModel(private val remoteAuthRepository: RemoteAuthRepository) : ViewModel() {

    private val _state = MutableLiveData<ResetPasswordUiState>(ResetPasswordUiState.Idle)
    val state: LiveData<ResetPasswordUiState> = _state

    fun onSubmitClicked(codeInput: String, passwordInput: String, confirmPasswordInput: String) {
        val code = codeInput.trim()
        if (code.isEmpty()) {
            _state.value = ResetPasswordUiState.FieldError(ResetPasswordField.CODE, "Enter the code from your email")
            return
        }
        if (!FieldValidators.isValidPassword(passwordInput)) {
            _state.value = ResetPasswordUiState.FieldError(
                ResetPasswordField.PASSWORD, "Password must be at least 8 characters and include a letter and a number")
            return
        }
        if (confirmPasswordInput != passwordInput) {
            _state.value = ResetPasswordUiState.FieldError(ResetPasswordField.CONFIRM_PASSWORD, "Passwords do not match")
            return
        }

        _state.value = ResetPasswordUiState.Loading
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { remoteAuthRepository.resetPassword(code, passwordInput) }
            _state.value = when (outcome) {
                RemoteResetPasswordOutcome.Success -> ResetPasswordUiState.Success
                is RemoteResetPasswordOutcome.Rejected -> ResetPasswordUiState.FormError(messageFor(outcome.reason))
                is RemoteResetPasswordOutcome.NetworkFailure ->
                    ResetPasswordUiState.FormError("Can't reach the server. Check your connection and try again.")
            }
        }
    }

    private fun messageFor(reason: RemoteResetPasswordRejection): String = when (reason) {
        RemoteResetPasswordRejection.INVALID_CODE -> "That code isn't valid. Check your email and try again."
        RemoteResetPasswordRejection.EXPIRED -> "That code has expired. Contact a Backoffice/Grid Operator user for a new one."
        RemoteResetPasswordRejection.ALREADY_USED -> "That code has already been used."
        RemoteResetPasswordRejection.UNKNOWN -> "Unable to reset your password right now."
    }
}
