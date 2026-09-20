package com.example.smart_solar_mgt_app.data.repository.transaction

import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.domain.model.EnergyTransferVerification
import com.example.smart_solar_mgt_app.domain.model.Transaction

/**
 * Where TransactionRepositoryImpl actually gets its "is this pass valid / mark it complete"
 * answers from - originally on-device HMAC + local SQLite, now an authoritative online round-trip
 * to the C# Web API ([RemoteTransactionVerificationSource]), matching this interface's
 * always-intended cross-device server verification design.
 *
 * TransactionRepositoryImpl only does the Android-session concerns (role guard, resolving the
 * acting operator's NIC) and delegates the actual validity/completion decision here - neither
 * implementation of this interface should need to know SecurityManager exists. This is the one
 * seam that had to change to move from local to remote verification; OperatorScanFragment and
 * TransactionRepositoryImpl's public API never needed to.
 */
interface TransactionVerificationSource {
    /**
     * Given the raw scanned string and the acting operator's NIC, decide validity and return
     * display-ready details. Read-only - no writes.
     */
    fun verify(rawToken: String, operatorNic: String): AppResult<EnergyTransferVerification>

    /** Commits the completion. The authoritative source decides success/failure. */
    fun complete(transactionId: String, operatorNic: String): AppResult<Transaction>
}
