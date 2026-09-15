package com.example.smart_solar_mgt_app.domain.model

data class User(
    val nic: String,
    val name: String,
    val email: String,
    val phone: String?,
    val address: String?,
    val passwordHash: String,
    val role: Role,
    val accountStatus: AccountStatus
)
