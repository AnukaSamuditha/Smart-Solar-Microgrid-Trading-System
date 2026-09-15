package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.domain.model.EnergyTransferVerification
import com.example.smart_solar_mgt_app.domain.model.Transaction

interface TransactionRepository {
    /**
     * The Energy Transfer Pass for a booking, ownership-checked against [prosumerNic]. Fails
     * NotFound if the booking doesn't exist or isn't APPROVED yet, Unauthorized if it belongs to
     * a different prosumer, and NotFound again if somehow no GENERATED/SCANNED transaction row
     * exists for a APPROVED booking (structurally shouldn't happen - approveBooking creates the
     * transaction atomically with the APPROVED transition).
     */
    fun getEnergyTransferPass(bookingId: String, prosumerNic: String): AppResult<Transaction>

    /**
     * GRID_OPERATOR only. Read-only: verifies the scanned QR token's HMAC signature and expiry,
     * then looks up the transaction/booking/station for display - no writes. Fails Unknown for a
     * tampered/expired token, NotFound/InvalidStatusTransition if the transaction or booking has
     * already moved on (e.g. re-scanning an already-completed pass). The operator reviews this
     * result and explicitly taps "Complete Transfer" (calling [completeTransfer]) rather than the
     * scan alone committing anything.
     */
    fun verifyToken(rawToken: String): AppResult<EnergyTransferVerification>

    /**
     * GRID_OPERATOR only. Re-checks transaction.status/booking.status atomically (the same
     * re-check-don't-trust-the-caller pattern as booking modify/cancel/approve), then marks the
     * transaction COMPLETED and its booking COMPLETED. Only reachable after a successful
     * [verifyToken] plus an explicit operator confirmation.
     */
    fun completeTransfer(transactionId: String): AppResult<Transaction>
}
