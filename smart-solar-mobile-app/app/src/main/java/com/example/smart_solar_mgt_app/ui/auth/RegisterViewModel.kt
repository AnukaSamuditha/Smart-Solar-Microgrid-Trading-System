package com.example.smart_solar_mgt_app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.core.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Local-first: writes the prosumer's own `users` row and queues a PROSUMER_REGISTER outbox
 * operation in one local transaction (LocalDbManager.registerProsumerLocally), then kicks a sync
 * attempt - this succeeds and shows Pending Activation immediately whether or not there's
 * connectivity right now. No password is collected here; that happens after approval (see
 * ResetPasswordActivity). SyncWorker is what actually calls POST /api/v1/prosumers/register.
 */
class RegisterViewModel(
    private val localDbManager: LocalDbManager,
    private val syncManager: SyncManager
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
            val result = withContext(Dispatchers.IO) { registerLocally(input) }
            _state.value = result
        }
    }

    private fun registerLocally(input: RegistrationValidator.Input): RegisterUiState {
        val nic = input.nic.trim()
        val email = input.email.trim()
        val fullName = input.fullName.trim()
        val phone = input.phone.trim()
        val address = input.address.trim()

        val payloadJson = JSONObject()
            .put("nic", nic)
            .put("email", email)
            .put("fullName", fullName)
            .put("phone", phone)
            .put("address", address)
            .toString()

        return when (val result = localDbManager.registerProsumerLocally(nic, fullName, email, phone, address, payloadJson)) {
            is AppResult.Success -> {
                // Fires now if online; otherwise the periodic safety-net worker (or the next
                // connectivity window, via the worker's NetworkType.CONNECTED constraint) picks it up.
                syncManager.scheduleImmediateSync()
                RegisterUiState.PendingActivation(nic, email)
            }
            is AppResult.Failure -> when (val error = result.error) {
                is AppError.UniqueConstraintViolation -> when (error.field) {
                    "nic" -> RegisterUiState.FieldErrors(mapOf(RegisterField.NIC to "This NIC is already registered"))
                    else -> RegisterUiState.FieldErrors(mapOf(RegisterField.EMAIL to "This email is already registered"))
                }
                else -> RegisterUiState.FormError("Registration failed. Please try again.")
            }
        }
    }
}
