package com.example.smart_solar_mgt_app.core.security

import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.Session

sealed class LoginResult {
    data class Success(val session: Session) : LoginResult()
    data object InvalidCredentials : LoginResult()
    data class AccountNotActive(val status: AccountStatus) : LoginResult()

    /** A remote (Web Service) login rejection or network failure, already rendered to a user-facing message. */
    data class Error(val message: String) : LoginResult()

    // remote-prosumer-login-only terminal outcomes (see AuthEndpoints.ProsumerLoginAsync) - each
    // routes to its own dedicated screen rather than an inline message, so they're distinct
    // cases rather than folded into Error
    data object PendingApproval : LoginResult()
    data object AccountCreationDenied : LoginResult()
    data object PasswordNotSet : LoginResult()
}