package com.example.smart_solar_mgt_app.ui.prosumer.qrpass

import android.graphics.Bitmap

sealed class QrPassUiState {
    data object Loading : QrPassUiState()

    data class Loaded(
        val qrBitmap: Bitmap,
        val stationName: String,
        val dateLabel: String,
        val timeLabel: String,
        val energyAmount: Double,
        val transactionId: String,
        val expiryLabel: String
    ) : QrPassUiState()

    data class Error(val message: String) : QrPassUiState()
}
