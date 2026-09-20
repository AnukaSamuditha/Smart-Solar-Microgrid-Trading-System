package com.example.smart_solar_mgt_app.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Session
import com.example.smart_solar_mgt_app.domain.model.User

class AuthRepositoryImpl(private val localDbManager: LocalDbManager) : AuthRepository {

    override fun findUserByNic(nic: String): User? = localDbManager.getUserByNic(nic)

    override fun findUserByEmail(email: String): User? = localDbManager.getUserByEmail(email)

    override fun createUser(user: User): AppResult<User> = try {
        localDbManager.insertUser(user)
        AppResult.Success(user)
    } catch (e: SQLiteConstraintException) {
        AppResult.Failure(AppError.UniqueConstraintViolation("nic_or_email"))
    }

    override fun updateProfile(nic: String, name: String, email: String, phone: String?, address: String?): AppResult<User> {
        val current = localDbManager.getUserByNic(nic) ?: return AppResult.Failure(AppError.NotFound)
        val updated = current.copy(name = name, email = email, phone = phone, address = address)
        return try {
            localDbManager.updateUser(updated)
            AppResult.Success(updated)
        } catch (e: SQLiteConstraintException) {
            AppResult.Failure(AppError.UniqueConstraintViolation("email"))
        }
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