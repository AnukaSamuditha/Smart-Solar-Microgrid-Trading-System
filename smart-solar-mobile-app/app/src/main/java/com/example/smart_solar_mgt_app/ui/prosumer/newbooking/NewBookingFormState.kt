package com.example.smart_solar_mgt_app.ui.prosumer.newbooking

import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.MicrogridNode

sealed class NewBookingFormState {
    data object Idle : NewBookingFormState()
    data object Saving : NewBookingFormState()
    data class FieldErrors(val errors: Map<BookingField, String>) : NewBookingFormState()
    data class FormError(val message: String) : NewBookingFormState()
    data class Created(val booking: Booking, val node: MicrogridNode) : NewBookingFormState()
    data class Updated(val booking: Booking, val node: MicrogridNode) : NewBookingFormState()
}
