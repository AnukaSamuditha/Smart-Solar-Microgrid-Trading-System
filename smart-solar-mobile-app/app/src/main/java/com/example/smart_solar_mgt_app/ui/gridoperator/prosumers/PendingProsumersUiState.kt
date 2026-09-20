package com.example.smart_solar_mgt_app.ui.gridoperator.prosumers

sealed class PendingProsumersUiState {
    data object Loading : PendingProsumersUiState()
    data object Empty : PendingProsumersUiState()
    data class Loaded(val items: List<PendingProsumerItem>) : PendingProsumersUiState()
    data class Error(val message: String) : PendingProsumersUiState()
}

data class PendingProsumerItem(
    val nic: String,
    val fullName: String?,
    val email: String,
    val phone: String?,
    val address: String?
)
