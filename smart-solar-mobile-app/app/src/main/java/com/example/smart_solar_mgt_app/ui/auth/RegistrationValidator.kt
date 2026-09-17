package com.example.smart_solar_mgt_app.ui.auth

import com.example.smart_solar_mgt_app.util.FieldValidators

enum class RegisterField { NIC, FULL_NAME, EMAIL, PHONE, ADDRESS, PASSWORD, CONFIRM_PASSWORD }

object RegistrationValidator {

    data class Input(
        val nic: String,
        val fullName: String,
        val email: String,
        val phone: String,
        val address: String,
        val password: String,
        val confirmPassword: String
    )

    /** Aggregates every field error at once rather than failing fast on the first one. */
    fun validate(input: Input): Map<RegisterField, String> {
        val errors = mutableMapOf<RegisterField, String>()

        when {
            input.nic.isBlank() -> errors[RegisterField.NIC] = "NIC is required"
            !FieldValidators.isValidNic(input.nic) -> errors[RegisterField.NIC] = "Enter a valid NIC number"
        }

        when {
            input.fullName.isBlank() -> errors[RegisterField.FULL_NAME] = "Full name is required"
            input.fullName.trim().length < 2 -> errors[RegisterField.FULL_NAME] = "Full name is required"
        }

        when {
            input.email.isBlank() -> errors[RegisterField.EMAIL] = "Email is required"
            !FieldValidators.isValidEmail(input.email) -> errors[RegisterField.EMAIL] = "Enter a valid email address"
        }

        when {
            input.phone.isBlank() -> errors[RegisterField.PHONE] = "Phone number is required"
            !FieldValidators.isValidPhone(input.phone) -> errors[RegisterField.PHONE] = "Enter a valid phone number"
        }

        if (input.address.isBlank()) errors[RegisterField.ADDRESS] = "Address is required"

        when {
            input.password.isBlank() -> errors[RegisterField.PASSWORD] = "Password is required"
            !FieldValidators.isValidPassword(input.password) ->
                errors[RegisterField.PASSWORD] = "Password must be at least 8 characters and include a letter and a number"
        }

        when {
            input.confirmPassword.isBlank() -> errors[RegisterField.CONFIRM_PASSWORD] = "Confirm your password"
            input.confirmPassword != input.password -> errors[RegisterField.CONFIRM_PASSWORD] = "Passwords do not match"
        }

        return errors
    }
}
