package com.example.smart_solar_mgt_app.di

import android.content.Context
import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.core.network.ApiClient
import com.example.smart_solar_mgt_app.core.security.SecureSessionStore
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.core.security.SecurityManagerImpl
import com.example.smart_solar_mgt_app.core.sync.SyncManager
import com.example.smart_solar_mgt_app.core.sync.SyncManagerImpl
import com.example.smart_solar_mgt_app.data.repository.AuthRepository
import com.example.smart_solar_mgt_app.data.repository.AuthRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.data.repository.BookingRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.RemoteAuthRepository
import com.example.smart_solar_mgt_app.data.repository.RemoteAuthRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.NodeRepository
import com.example.smart_solar_mgt_app.data.repository.NodeRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.RemoteNodeRepository
import com.example.smart_solar_mgt_app.data.repository.RemoteNodeRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.RemoteProsumerRepository
import com.example.smart_solar_mgt_app.data.repository.RemoteProsumerRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.RemoteReservationRepository
import com.example.smart_solar_mgt_app.data.repository.RemoteReservationRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.RemoteTransactionRepository
import com.example.smart_solar_mgt_app.data.repository.RemoteTransactionRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.TransactionRepository
import com.example.smart_solar_mgt_app.data.repository.TransactionRepositoryImpl
import com.example.smart_solar_mgt_app.data.repository.transaction.RemoteTransactionVerificationSource
import com.example.smart_solar_mgt_app.data.repository.transaction.TransactionVerificationSource

/**
 * Manual dependency container. Managers/repositories are added here as each
 * architectural piece is implemented (CommunicationManager, SyncManager, etc.).
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val localDbManager: LocalDbManager by lazy { LocalDbManager(appContext) }
    val secureSessionStore: SecureSessionStore by lazy { SecureSessionStore(appContext) }
    val authRepository: AuthRepository by lazy { AuthRepositoryImpl(localDbManager) }
    val remoteAuthRepository: RemoteAuthRepository by lazy { RemoteAuthRepositoryImpl(ApiClient.httpClient, secureSessionStore) }
    val remoteProsumerRepository: RemoteProsumerRepository by lazy {
        RemoteProsumerRepositoryImpl(ApiClient.httpClient, secureSessionStore, remoteAuthRepository)
    }
    val remoteNodeRepository: RemoteNodeRepository by lazy {
        RemoteNodeRepositoryImpl(ApiClient.httpClient, secureSessionStore, remoteAuthRepository)
    }
    val remoteReservationRepository: RemoteReservationRepository by lazy {
        RemoteReservationRepositoryImpl(ApiClient.httpClient, secureSessionStore, remoteAuthRepository)
    }
    val remoteTransactionRepository: RemoteTransactionRepository by lazy {
        RemoteTransactionRepositoryImpl(ApiClient.httpClient, secureSessionStore, remoteAuthRepository)
    }
    val nodeRepository: NodeRepository by lazy { NodeRepositoryImpl(localDbManager, remoteNodeRepository) }
    val bookingRepository: BookingRepository by lazy { BookingRepositoryImpl(localDbManager, securityManager, syncManager) }
    // Server-verified now: an authoritative online round-trip to the backend
    // (RemoteTransactionRepository) instead of the old on-device HMAC+local-SQLite check.
    // TransactionRepositoryImpl and everything above it (including OperatorScanFragment) never
    // needed to change - this is the one binding the seam was designed to let move.
    val transactionVerificationSource: TransactionVerificationSource by lazy {
        RemoteTransactionVerificationSource(remoteTransactionRepository)
    }
    val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(securityManager, transactionVerificationSource)
    }
    val securityManager: SecurityManager by lazy {
        SecurityManagerImpl(authRepository, secureSessionStore, remoteAuthRepository, remoteProsumerRepository)
    }
    val syncManager: SyncManager by lazy { SyncManagerImpl(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun applicationContext(): Context = appContext
}