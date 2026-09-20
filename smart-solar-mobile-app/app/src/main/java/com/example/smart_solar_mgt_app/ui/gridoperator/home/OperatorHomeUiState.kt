package com.example.smart_solar_mgt_app.ui.gridoperator.home

sealed class OperatorHomeUiState {
    data object Loading : OperatorHomeUiState()
    data object Empty : OperatorHomeUiState()
    data class Loaded(val items: List<PendingApprovalItem>) : OperatorHomeUiState()
    data class Error(val message: String) : OperatorHomeUiState()
}

data class PendingApprovalItem(
    val bookingId: String,
    val prosumerNic: String,
    val stationName: String,
    val bookingDate: String,
    val bookingTime: String,
    val energyAmount: Double
)
