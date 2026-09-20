package com.example.smart_solar_mgt_app.domain.model

data class User(
    val nic: String,
    val name: String,
    val email: String,
    val phone: String?,
    val address: String?,
    // null for a self-registered prosumer still awaiting review/password reset - see
    // SecurityManagerImpl.login, which always defers such a row to the remote login endpoint
    val passwordHash: String?,
    val role: Role,
    val accountStatus: AccountStatus
)
