package com.example.smart_solar_mgt_app.util

import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Single source of truth for the 12-hour modify/cancel notice rule - used both for client-side
 * UI gating (BookingDetailFragment) and the server-side re-check inside LocalDbManager's
 * transaction. Never trust the client-side check alone: time can elapse between opening a form
 * and submitting it.
 */
object BookingTimeRules {

    private val NOTICE_PERIOD: Duration = Duration.ofHours(12)

    fun hoursRemaining(bookingDate: LocalDate, bookingTime: LocalTime): Duration =
        Duration.between(LocalDateTime.now(), LocalDateTime.of(bookingDate, bookingTime))

    fun canModifyOrCancel(status: BookingStatus, bookingDate: LocalDate, bookingTime: LocalTime): Boolean {
        if (status != BookingStatus.PENDING && status != BookingStatus.APPROVED) return false
        return hoursRemaining(bookingDate, bookingTime) >= NOTICE_PERIOD
    }
}
