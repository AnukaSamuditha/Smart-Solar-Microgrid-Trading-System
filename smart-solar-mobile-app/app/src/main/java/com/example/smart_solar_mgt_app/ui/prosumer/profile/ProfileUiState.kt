package com.example.smart_solar_mgt_app.ui.prosumer.profile

import com.example.smart_solar_mgt_app.domain.model.User

sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Loaded(val user: User) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}
