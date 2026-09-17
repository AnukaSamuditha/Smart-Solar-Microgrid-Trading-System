package com.example.smart_solar_mgt_app.di

import android.content.Context
import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.core.security.QrTokenService
import com.example.smart_solar_mgt_app.core.security.SecureSessionStore
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.core.security.SecurityManagerImpl
import com.example.smart_solar_mgt_app.data.repository.AuthRepository
import com.example.smart_solar_mgt_app.data.repository.AuthRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.data.repository.BookingRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.StationRepository
import com.example.smart_solar_mgt_app.data.repository.StationRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.TransactionRepository
import com.example.smart_solar_mgt_app.data.repository.TransactionRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.transaction.LocalTransactionVerificationSource
import com.example.smart_solar_mgt_app.data.repository.transaction.TransactionVerificationSource

/**
 * Manual dependency container. Managers/repositories are added here as each
 * architectural piece is implemented (CommunicationManager, SyncManager, etc.).
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val localDbManager: LocalDbManager by lazy { LocalDbManager(appContext) }
    val secureSessionStore: SecureSessionStore by lazy { SecureSessionStore(appContext) }
    val qrTokenService: QrTokenService by lazy { QrTokenService() }
    val authRepository: AuthRepository by lazy { AuthRepositoryImpl(localDbManager) }
    val bookingRepository: BookingRepository by lazy { BookingRepositoryImpl(localDbManager, securityManager, qrTokenService) }
    val stationRepository: StationRepository by lazy { StationRepositoryImpl(localDbManager) }
    // The only binding that changes when cross-device server verification ships: swap this for
    // a RemoteTransactionVerificationSource. TransactionRepositoryImpl and everything above it
    // (including OperatorScanFragment) never need to change.
    val transactionVerificationSource: TransactionVerificationSource by lazy {
        LocalTransactionVerificationSource(localDbManager, qrTokenService)
    }
    val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(localDbManager, securityManager, transactionVerificationSource)
    }
    val securityManager: SecurityManager by lazy {
        SecurityManagerImpl(authRepository, secureSessionStore, qrTokenService)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun applicationContext(): Context = appContext
}