package com.example.smart_solar_mgt_app.ui.prosumer.newbooking

import java.time.LocalDate
import java.time.LocalTime

enum class BookingField { STATION, SLOT, DATE, TIME, ENERGY_AMOUNT }

object BookingValidator {

    data class Input(
        val nodeId: String?,
        val slotId: String?,
        val date: LocalDate?,
        val time: LocalTime?,
        val energyAmountText: String
    )

    /** Aggregates every field error at once. [nodeCapacityKw] is null if no node is resolved yet. */
    fun validate(input: Input, nodeCapacityKw: Double?): Map<BookingField, String> {
        val errors = mutableMapOf<BookingField, String>()

        if (input.nodeId == null) {
            errors[BookingField.STATION] = "Select a station"
        } else if (input.slotId == null) {
            errors[BookingField.SLOT] = "Select an available slot"
        }

        val today = LocalDate.now()
        val maxDate = today.plusDays(7)
        when {
            input.date == null -> errors[BookingField.DATE] = "Reservation date is required"
            input.date.isBefore(today) -> errors[BookingField.DATE] = "Reservation date cannot be in the past"
            input.date.isAfter(maxDate) -> errors[BookingField.DATE] = "Reservation must be within the next 7 days"
        }

        when {
            input.time == null -> errors[BookingField.TIME] = "Reservation time is required"
            input.date == today && input.time.isBefore(LocalTime.now()) ->
                errors[BookingField.TIME] = "Reservation time cannot be in the past"
        }

        val energyAmount = input.energyAmountText.toDoubleOrNull()
        when {
            input.energyAmountText.isBlank() -> errors[BookingField.ENERGY_AMOUNT] = "Energy amount is required"
            energyAmount == null || energyAmount <= 0 ->
                errors[BookingField.ENERGY_AMOUNT] = "Enter a valid energy amount"
            nodeCapacityKw != null && energyAmount > nodeCapacityKw ->
                errors[BookingField.ENERGY_AMOUNT] = "Energy amount exceeds this station's capacity"
        }

        return errors
    }
}
