package com.example.smart_solar_mgt_app.util

import android.util.Patterns

object FieldValidators {
    private val NIC_REGEX = Regex("^(\\d{9}[VvXx]|\\d{12})$")
    private val PHONE_REGEX = Regex("^0\\d{9}$")

    fun isValidNic(nic: String): Boolean = NIC_REGEX.matches(nic.trim())

    fun isValidEmail(email: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    fun isValidPhone(phone: String): Boolean = PHONE_REGEX.matches(phone.trim())

    fun isValidPassword(password: String): Boolean =
        password.length >= 8 && password.any { it.isDigit() } && password.any { it.isLetter() }
}
