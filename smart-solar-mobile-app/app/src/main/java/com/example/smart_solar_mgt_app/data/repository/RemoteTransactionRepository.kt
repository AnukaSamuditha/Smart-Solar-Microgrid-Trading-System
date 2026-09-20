package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.RemoteTransactionCompleteOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionGenerateOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionScanOutcome

/** QR Energy Transfer Pass calls against the real Web Service (smart-solar-mgt-api). */
interface RemoteTransactionRepository {
    /** Prosumer self-service - POST /api/v1/transactions/generate. */
    fun generate(reservationId: String): RemoteTransactionGenerateOutcome

    /** Grid-Operator-only - POST /api/v1/transactions/scan. */
    fun scan(rawToken: String): RemoteTransactionScanOutcome

    /** Grid-Operator-only - PATCH /api/v1/transactions/{id}/complete. */
    fun complete(transactionId: String): RemoteTransactionCompleteOutcome
}
