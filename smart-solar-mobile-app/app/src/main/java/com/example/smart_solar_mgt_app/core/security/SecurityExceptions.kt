package com.example.smart_solar_mgt_app.core.security

/** Base for authentication/authorization failures. Named to avoid colliding with java.lang.SecurityException. */
sealed class AuthException(message: String) : Exception(message)

class UnauthorizedAccessException(message: String = "Not authorized") : AuthException(message)