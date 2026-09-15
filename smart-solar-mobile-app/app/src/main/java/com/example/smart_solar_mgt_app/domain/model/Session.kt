package com.example.smart_solar_mgt_app.domain.model

data class Session(
    val userId: String,
    val role: Role,
    val loginState: LoginState
)