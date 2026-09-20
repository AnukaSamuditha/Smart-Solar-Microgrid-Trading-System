package com.example.smart_solar_mgt_app.data.repository.transaction

import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionCompleteOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionCompleteRejection
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionScanOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionScanRejection
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionVerification
import com.example.smart_solar_mgt_app.data.repository.RemoteTransactionRepository
import com.example.smart_solar_mgt_app.domain.model.EnergyTransferVerification
import com.example.smart_solar_mgt_app.domain.model.Transaction
import com.example.smart_solar_mgt_app.domain.model.TransactionStatus
import com.example.smart_solar_mgt_app.util.DateFormats
import java.time.ZoneId

/**
 * The server-verified TransactionVerificationSource: an authoritative online round-trip to
 * smart-solar-mgt-api (RemoteTransactionRepository) replacing LocalTransactionVerificationSource's
 * on-device HMAC+local-SQLite check - this is the exact swap the seam's own doc comment
 * described. TransactionRepositoryImpl and OperatorScanFragment need no changes at all; only
 * this class and the ServiceLocator binding that wires it in are new.
 */
class RemoteTransactionVerificationSource(
    private val remoteTransactionRepository: RemoteTransactionRepository
) : TransactionVerificationSource {

    override fun verify(rawToken: String, operatorNic: String): AppResult<EnergyTransferVerification> =
        when (val outcome = remoteTransactionRepository.scan(rawToken)) {
            is RemoteTransactionScanOutcome.Success -> AppResult.Success(outcome.verification.toEnergyTransferVerification())
            is RemoteTransactionScanOutcome.Rejected -> AppResult.Failure(mapScanRejection(outcome.reason))
            is RemoteTransactionScanOutcome.NetworkFailure -> AppResult.Failure(AppError.Unknown(outcome.message))
        }

    override fun complete(transactionId: String, operatorNic: String): AppResult<Transaction> =
        when (val outcome = remoteTransactionRepository.complete(transactionId)) {
            is RemoteTransactionCompleteOutcome.Success -> AppResult.Success(outcome.verification.toLocalTransaction())
            is RemoteTransactionCompleteOutcome.Rejected -> AppResult.Failure(mapCompleteRejection(outcome.reason))
            is RemoteTransactionCompleteOutcome.NetworkFailure -> AppResult.Failure(AppError.Unknown(outcome.message))
        }

    private fun RemoteTransactionVerification.toEnergyTransferVerification(): EnergyTransferVerification {
        val zonedStart = startTime?.atZone(ZoneId.systemDefault())
        return EnergyTransferVerification(
            transactionId = transactionId,
            bookingId = reservationId,
            prosumerNic = prosumerNic,
            stationName = nodeName ?: reservationId,
            bookingDate = zonedStart?.toLocalDate()?.toString().orEmpty(),
            bookingTime = zonedStart?.toLocalTime()?.format(DateFormats.TIME_FORMATTER).orEmpty(),
            energyAmount = energyAmount ?: 0.0
        )
    }

    // Transaction carries a few fields (qrToken, operatorId, generatedAt) that only ever mattered
    // for the old on-device signing scheme and that the server's completion response doesn't
    // return - left as placeholders since only .transactionId is read after completion
    // (OperatorScanFragment's confirmation dialog).
    private fun RemoteTransactionVerification.toLocalTransaction(): Transaction = Transaction(
        transactionId = transactionId,
        bookingId = reservationId,
        qrToken = "",
        status = mapRemoteStatus(status),
        operatorId = null,
        generatedAt = 0L,
        completedAt = System.currentTimeMillis()
    )

    private fun mapRemoteStatus(status: String): TransactionStatus = when (status) {
        "Generated" -> TransactionStatus.GENERATED
        "Scanned" -> TransactionStatus.SCANNED
        "Completed" -> TransactionStatus.COMPLETED
        else -> TransactionStatus.GENERATED
    }

    private fun mapScanRejection(reason: RemoteTransactionScanRejection): AppError = when (reason) {
        RemoteTransactionScanRejection.NOT_FOUND -> AppError.NotFound
        RemoteTransactionScanRejection.ALREADY_USED -> AppError.InvalidStatusTransition
        RemoteTransactionScanRejection.RESERVATION_NO_LONGER_CONFIRMED -> AppError.InvalidStatusTransition
        RemoteTransactionScanRejection.EXPIRED -> AppError.Unknown("This QR code has expired.")
        RemoteTransactionScanRejection.UNKNOWN -> AppError.Unknown("Could not verify this QR code. Please try again.")
    }

    private fun mapCompleteRejection(reason: RemoteTransactionCompleteRejection): AppError = when (reason) {
        RemoteTransactionCompleteRejection.NOT_FOUND -> AppError.NotFound
        RemoteTransactionCompleteRejection.NOT_SCANNED -> AppError.InvalidStatusTransition
        RemoteTransactionCompleteRejection.UNKNOWN -> AppError.Unknown("Could not complete this transfer. Please try again.")
    }
}
