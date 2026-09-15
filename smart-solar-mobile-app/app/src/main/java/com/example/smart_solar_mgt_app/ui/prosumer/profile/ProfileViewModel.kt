package com.example.smart_solar_mgt_app.ui.prosumer.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.AuthRepository
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _state = MutableLiveData<ProfileUiState>(ProfileUiState.Loading)
    val state: LiveData<ProfileUiState> = _state

    fun loadProfile() {
        val nic = securityManager.currentSession()?.userId ?: return
        _state.value = ProfileUiState.Loading
        viewModelScope.launch {
            val user = withContext(Dispatchers.IO) { authRepository.findUserByNic(nic) }
            _state.value = if (user != null) {
                ProfileUiState.Loaded(user)
            } else {
                // Session points at a user row that no longer exists - a broken invariant, not a normal error.
                ProfileUiState.Error("Your session is no longer valid. Please log in again.")
            }
        }
    }

    fun requestDeactivation() {
        val nic = securityManager.currentSession()?.userId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { authRepository.updateAccountStatus(nic, AccountStatus.DEACTIVATION_REQUESTED) }
            loadProfile()
        }
    }

    fun logout() = securityManager.logout()
}
