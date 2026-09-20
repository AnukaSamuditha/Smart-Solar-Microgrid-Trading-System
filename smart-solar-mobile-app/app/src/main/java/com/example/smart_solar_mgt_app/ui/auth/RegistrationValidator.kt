package com.example.smart_solar_mgt_app.ui.auth

import com.example.smart_solar_mgt_app.util.FieldValidators

enum class RegisterField { NIC, FULL_NAME, EMAIL, PHONE, ADDRESS }

object RegistrationValidator {

    /**
     * No password field - self-registered prosumers set a password only after a Backoffice/Grid
     * Operator reviewer approves the request (see ResetPasswordActivity), not at submission time.
     */
    data class Input(
        val nic: String,
        val fullName: String,
        val email: String,
        val phone: String,
        val address: String
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

        return errors
    }
}
