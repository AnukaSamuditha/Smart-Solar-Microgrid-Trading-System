package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.transaction.TransactionVerificationSource
import com.example.smart_solar_mgt_app.domain.model.EnergyTransferVerification
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Transaction

/**
 * Pure orchestration: role guard + resolving the acting operator's identity, then delegates the
 * actual "is this valid / mark it complete" decision to whichever TransactionVerificationSource
 * is wired in ServiceLocator (a remote call to the C# API - see RemoteTransactionVerificationSource).
 * Neither this class' public API nor OperatorScanFragment change when that source is swapped.
 */
class TransactionRepositoryImpl(
    private val securityManager: SecurityManager,
    private val verificationSource: TransactionVerificationSource
) : TransactionRepository {

    override fun verifyToken(rawToken: String): AppResult<EnergyTransferVerification> {
        securityManager.requireRole(Role.GRID_OPERATOR)
        val operatorNic = securityManager.currentSession()?.userId
            ?: return AppResult.Failure(AppError.Unauthorized)
        return verificationSource.verify(rawToken, operatorNic)
    }

    override fun completeTransfer(transactionId: String): AppResult<Transaction> {
        securityManager.requireRole(Role.GRID_OPERATOR)
        val operatorNic = securityManager.currentSession()?.userId
            ?: return AppResult.Failure(AppError.Unauthorized)
        return verificationSource.complete(transactionId, operatorNic)
    }
}
