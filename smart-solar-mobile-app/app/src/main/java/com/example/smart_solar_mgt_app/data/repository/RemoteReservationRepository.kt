package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.RemoteReservationActionOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationListOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationRequestOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationReviewOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationUpdateOutcome
import java.time.Instant

/**
 * Reservation calls against the real Web Service (smart-solar-mgt-api). BookingRepositoryImpl is
 * the sole translation boundary between this "Reservation" wire vocabulary and the app's own
 * "Booking" domain vocabulary - see its file-level comment.
 */
interface RemoteReservationRepository {
    /** Prosumer self-service - POST /api/v1/reservations/mine. Starts Pending. */
    fun request(nodeId: String, slotId: String, startTime: Instant, endTime: Instant, energyAmount: Double?): RemoteReservationRequestOutcome

    /** Prosumer self-service - PATCH /api/v1/reservations/mine/{id}. Reschedule only - node/slot stay fixed. */
    fun update(reservationId: String, startTime: Instant, endTime: Instant): RemoteReservationUpdateOutcome

    /** Prosumer self-service - PATCH /api/v1/reservations/mine/{id}/cancel. */
    fun cancel(reservationId: String): RemoteReservationActionOutcome

    /** Backoffice/Grid Operator - PATCH /api/v1/reservations/{id}/approve. */
    fun approve(reservationId: String): RemoteReservationReviewOutcome

    /** Backoffice/Grid Operator - PATCH /api/v1/reservations/{id}/reject. */
    fun reject(reservationId: String, reason: String?): RemoteReservationReviewOutcome

    /** Backoffice/Grid Operator - GET /api/v1/reservations?status=Pending. Cross-prosumer -
     * every Pending request awaiting review, not just ones this device happens to have synced. */
    fun listPending(): RemoteReservationListOutcome
}
