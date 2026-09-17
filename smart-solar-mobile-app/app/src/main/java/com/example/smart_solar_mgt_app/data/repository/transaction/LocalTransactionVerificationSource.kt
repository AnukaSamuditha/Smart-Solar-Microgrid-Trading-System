package com.example.smart_solar_mgt_app.data.repository.transaction

import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.core.security.QrTokenService
import com.example.smart_solar_mgt_app.core.security.QrValidationResult
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.domain.model.EnergyTransferVerification
import com.example.smart_solar_mgt_app.domain.model.Transaction
import com.example.smart_solar_mgt_app.domain.model.TransactionStatus

/**
 * Today's only TransactionVerificationSource: on-device HMAC signature/expiry check
 * (QrTokenService) plus the local SQLite mirror (LocalDbManager). Every device only has its own
 * local database, so a QR generated on one device can't yet be treated as authoritative on
 * another - the known limitation this whole seam exists to eventually let a
 * RemoteTransactionVerificationSource fix without touching anything above this class.
 */
class LocalTransactionVerificationSource(
    private val localDbManager: LocalDbManager,
    private val qrTokenService: QrTokenService
) : TransactionVerificationSource {

    override fun verify(rawToken: String, operatorNic: String): AppResult<EnergyTransferVerification> {
        return when (val validation = qrTokenService.verify(rawToken)) {
            is QrValidationResult.Valid -> {
                val transaction = localDbManager.getTransactionById(validation.transactionId)
                    ?: return AppResult.Failure(AppError.NotFound)
                if (transaction.status != TransactionStatus.GENERATED) return AppResult.Failure(AppError.InvalidStatusTransition)

                val booking = localDbManager.getBookingById(transaction.bookingId)
                    ?: return AppResult.Failure(AppError.NotFound)
                if (booking.status != BookingStatus.APPROVED) return AppResult.Failure(AppError.InvalidStatusTransition)

                val stationName = localDbManager.getStationById(booking.stationId)?.stationName ?: booking.stationId
                AppResult.Success(
                    EnergyTransferVerification(
                        transactionId = transaction.transactionId,
                        bookingId = booking.bookingId,
                        prosumerNic = booking.prosumerNic,
                        stationName = stationName,
                        bookingDate = booking.bookingDate,
                        bookingTime = booking.bookingTime,
                        energyAmount = booking.energyAmount
                    )
                )
            }
            QrValidationResult.Tampered -> AppResult.Failure(AppError.Unknown("This QR code is invalid."))
            QrValidationResult.Expired -> AppResult.Failure(AppError.Unknown("This QR code has expired."))
        }
    }

    override fun complete(transactionId: String, operatorNic: String): AppResult<Transaction> =
        localDbManager.completeTransaction(transactionId, operatorNic, System.currentTimeMillis())
}
