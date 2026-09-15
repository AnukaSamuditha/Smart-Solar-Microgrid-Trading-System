package com.example.smart_solar_mgt_app.core.security

import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.Session

sealed class LoginResult {
    data class Success(val session: Session) : LoginResult()
    data object InvalidCredentials : LoginResult()
    data class AccountNotActive(val status: AccountStatus) : LoginResult()
}