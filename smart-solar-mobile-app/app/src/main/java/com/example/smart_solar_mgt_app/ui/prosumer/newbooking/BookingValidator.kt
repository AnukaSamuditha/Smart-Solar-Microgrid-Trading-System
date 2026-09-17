package com.example.smart_solar_mgt_app.ui.prosumer.newbooking

import java.time.LocalDate
import java.time.LocalTime

enum class BookingField { STATION, DATE, TIME, ENERGY_AMOUNT }

object BookingValidator {

    data class Input(
        val stationId: String?,
        val date: LocalDate?,
        val time: LocalTime?,
        val energyAmountText: String
    )

    /** Aggregates every field error at once. [stationCapacityKwh] is null if no station is resolved yet. */
    fun validate(input: Input, stationCapacityKwh: Double?): Map<BookingField, String> {
        val errors = mutableMapOf<BookingField, String>()

        if (input.stationId == null) {
            errors[BookingField.STATION] = "Select a station"
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
            stationCapacityKwh != null && energyAmount > stationCapacityKwh ->
                errors[BookingField.ENERGY_AMOUNT] = "Energy amount exceeds this station's capacity"
        }

        return errors
    }
}
