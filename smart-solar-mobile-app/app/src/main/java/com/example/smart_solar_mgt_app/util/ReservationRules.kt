package com.example.smart_solar_mgt_app.util

import java.time.Duration

/**
 * Single fixed session length applied to every reservation - the mobile booking form only
 * collects one date+time instant (no separate end-time picker), unlike the backend/web app's
 * explicit start+end window (see smart-solar-mgt-fe's create-reservation-dialog). BookingRepositoryImpl
 * derives the backend's required EndTime as StartTime + this duration. Revisit if the product
 * ever needs a prosumer-selectable session length.
 */
object ReservationRules {
    val SESSION_DURATION: Duration = Duration.ofHours(1)
}
