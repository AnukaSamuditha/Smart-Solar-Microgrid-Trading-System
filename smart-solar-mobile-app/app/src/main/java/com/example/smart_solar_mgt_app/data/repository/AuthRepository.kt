package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.Session
import com.example.smart_solar_mgt_app.domain.model.User
import com.example.smart_solar_mgt_app.core.common.AppResult

/**
 * Owns the `users` table for both auth and profile-editing concerns - one table, one repository.
 */
interface AuthRepository {
    fun findUserByNic(nic: String): User?
    fun findUserByEmail(email: String): User?
    fun createUser(user: User): AppResult<User>
    fun updateProfile(nic: String, name: String, email: String, phone: String?, address: String?): AppResult<User>
    fun updateAccountStatus(nic: String, status: AccountStatus)
    fun mirrorSessionToSqlite(session: Session)
    fun clearSessionMirror()
}