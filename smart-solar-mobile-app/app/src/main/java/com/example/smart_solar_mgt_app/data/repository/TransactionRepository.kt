package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.domain.model.EnergyTransferVerification
import com.example.smart_solar_mgt_app.domain.model.Transaction

interface TransactionRepository {
    /**
     * GRID_OPERATOR only. Read-only: verifies the scanned QR token against the backend, then
     * looks up the transaction/reservation/node for display - no writes. Fails Unknown for an
     * expired token, NotFound/InvalidStatusTransition if the transaction or reservation has
     * already moved on (e.g. re-scanning an already-completed pass). The operator reviews this
     * result and explicitly taps "Complete Transfer" (calling [completeTransfer]) rather than the
     * scan alone committing anything.
     */
    fun verifyToken(rawToken: String): AppResult<EnergyTransferVerification>

    /**
     * GRID_OPERATOR only. Marks the transaction COMPLETED via the backend, which also completes
     * the linked reservation and releases the node's slot. Only reachable after a successful
     * [verifyToken] plus an explicit operator confirmation.
     */
    fun completeTransfer(transactionId: String): AppResult<Transaction>
}
