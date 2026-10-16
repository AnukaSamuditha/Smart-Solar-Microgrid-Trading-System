package com.example.smart_solar_mgt_app.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerProfileOutcome
import com.example.smart_solar_mgt_app.core.sync.SyncManager
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.OutboxOperationType
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Session
import com.example.smart_solar_mgt_app.domain.model.User
import org.json.JSONObject

class AuthRepositoryImpl(
    private val localDbManager: LocalDbManager,
    private val syncManager: SyncManager,
    private val remoteProsumerRepository: RemoteProsumerRepository
) : AuthRepository {

    override fun findUserByNic(nic: String): User? = localDbManager.getUserByNic(nic)

    override fun findUserByEmail(email: String): User? = localDbManager.getUserByEmail(email)

    override fun createUser(user: User): AppResult<User> = try {
        localDbManager.insertUser(user)
        AppResult.Success(user)
    } catch (e: SQLiteConstraintException) {
        AppResult.Failure(AppError.UniqueConstraintViolation("nic_or_email"))
    }

    // Local-first like bookings: writes the cache row now and queues a PROFILE_UPDATE for the
    // backend. Email is never changed here - it's the login identifier and immutable from the
    // app (PUT /prosumers/me has no email field), so the [email] argument is ignored.
    override fun updateProfile(nic: String, name: String, email: String, phone: String?, address: String?): AppResult<User> {
        val current = localDbManager.getUserByNic(nic) ?: return AppResult.Failure(AppError.NotFound)
        val updated = current.copy(name = name, phone = phone, address = address)
        return try {
            localDbManager.updateUser(updated)
            val payloadJson = JSONObject()
                .put("fullName", name)
                .put("phone", phone.orEmpty())
                .put("address", address.orEmpty())
                .toString()
            localDbManager.enqueueOutboxOperation(OutboxOperationType.PROFILE_UPDATE, nic, payloadJson)
            syncManager.scheduleImmediateSync()
            AppResult.Success(updated)
        } catch (e: SQLiteConstraintException) {
            AppResult.Failure(AppError.UniqueConstraintViolation("email"))
        }
    }

    override fun refreshProfileFromServer(nic: String): Boolean {
        val outcome = try {
            remoteProsumerRepository.getMyProfile()
        } catch (e: Exception) {
            return false
        }
        if (outcome !is RemoteProsumerProfileOutcome.Success) return false
        val profile = outcome.profile
        if (profile.nic != nic) return false
        return try {
            localDbManager.applyServerProfile(
                nic = nic,
                fullName = profile.fullName ?: nic,
                email = profile.email,
                phone = profile.phone,
                address = profile.address,
                serverStatus = mapServerStatus(profile.status, localDbManager.getUserByNic(nic)?.accountStatus)
            )
        } catch (e: Exception) {
            false
        }
    }

    // backend ProsumerStatus -> local AccountStatus. DEACTIVATION_REQUESTED is a local-only
    // state (the backend has no equivalent), so an Active server status must not erase it.
    // Unknown/Invited statuses leave the local status alone.
    private fun mapServerStatus(serverStatus: String, local: AccountStatus?): AccountStatus? = when (serverStatus) {
        "Active" -> if (local == AccountStatus.DEACTIVATION_REQUESTED) local else AccountStatus.ACTIVE
        "Deactivated" -> AccountStatus.DEACTIVATED
        "PendingApproval" -> AccountStatus.PENDING_APPROVAL
        "Rejected" -> AccountStatus.REJECTED
        else -> null
    }

    override fun updateAccountStatus(nic: String, status: AccountStatus) {
        localDbManager.updateAccountStatus(nic, status)
    }

    override fun getUsersPendingActivation(): List<User> =
        localDbManager.getUsersByRoleAndStatus(Role.PROSUMER, AccountStatus.PENDING_APPROVAL)

    override fun mirrorSessionToSqlite(session: Session) {
        localDbManager.saveSessionMirror(session)
    }

    override fun clearSessionMirror() {
        localDbManager.clearSessionMirror()
    }

    override fun upsertProsumerProfileCache(nic: String, fullName: String, email: String, phone: String?, address: String?) {
        localDbManager.upsertProsumerProfileCache(nic, fullName, email, phone, address)
    }
}