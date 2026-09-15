package com.example.smart_solar_mgt_app.ui.prosumer.profile

import com.example.smart_solar_mgt_app.util.FieldValidators

enum class ProfileField { FULL_NAME, EMAIL, PHONE, ADDRESS }

object EditProfileValidator {

    data class Input(val fullName: String, val email: String, val phone: String, val address: String)

    /** Same rules as RegistrationValidator's contact-info fields, minus NIC/password. */
    fun validate(input: Input): Map<ProfileField, String> {
        val errors = mutableMapOf<ProfileField, String>()

        when {
            input.fullName.isBlank() -> errors[ProfileField.FULL_NAME] = "Full name is required"
            input.fullName.trim().length < 2 -> errors[ProfileField.FULL_NAME] = "Full name is required"
        }

        when {
            input.email.isBlank() -> errors[ProfileField.EMAIL] = "Email is required"
            !FieldValidators.isValidEmail(input.email) -> errors[ProfileField.EMAIL] = "Enter a valid email address"
        }

        when {
            input.phone.isBlank() -> errors[ProfileField.PHONE] = "Phone number is required"
            !FieldValidators.isValidPhone(input.phone) -> errors[ProfileField.PHONE] = "Enter a valid phone number"
        }

        if (input.address.isBlank()) errors[ProfileField.ADDRESS] = "Address is required"

        return errors
    }
}
