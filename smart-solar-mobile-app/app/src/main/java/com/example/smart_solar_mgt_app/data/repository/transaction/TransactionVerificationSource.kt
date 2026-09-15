package com.example.smart_solar_mgt_app.data.repository.transaction

import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.domain.model.EnergyTransferVerification
import com.example.smart_solar_mgt_app.domain.model.Transaction

/**
 * Where TransactionRepositoryImpl actually gets its "is this pass valid / mark it complete"
 * answers from - local SQLite + on-device HMAC today ([LocalTransactionVerificationSource]), the
 * C# Web API once cross-device server verification ships (a future RemoteTransactionVerificationSource).
 *
 * TransactionRepositoryImpl only does the Android-session concerns (role guard, resolving the
 * acting operator's NIC) and delegates the actual validity/completion decision here - neither
 * implementation of this interface should need to know SecurityManager exists. Swapping which
 * implementation is wired in ServiceLocator is the only change needed to move from local to
 * remote verification; OperatorScanFragment and TransactionRepositoryImpl's public API never change.
 */
interface TransactionVerificationSource {
    /**
     * Given the raw scanned string and the acting operator's NIC, decide validity and return
     * display-ready details. How "valid" is decided is entirely up to the implementation - local
     * HMAC+expiry check today, an HTTPS round-trip to the server tomorrow. Read-only - no writes.
     */
    fun verify(rawToken: String, operatorNic: String): AppResult<EnergyTransferVerification>

    /** Commits the completion. The authoritative source decides success/failure. */
    fun complete(transactionId: String, operatorNic: String): AppResult<Transaction>
}
