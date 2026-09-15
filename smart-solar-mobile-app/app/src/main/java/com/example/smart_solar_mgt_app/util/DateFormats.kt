package com.example.smart_solar_mgt_app.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Shared formats so stored booking_date/booking_time strings stay consistently zero-padded
 * and lexicographically sortable (relied on by LocalDbManager's date/time queries). */
object DateFormats {
    val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val DISPLAY_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** "Now" in the exact string format booking_date is stored in - the one place every
     * future/past comparison against the current moment should get its "now" from. */
    fun nowDateString(): String = LocalDate.now().toString()

    /** "Now" in the exact string format booking_time is stored in - see [nowDateString]. */
    fun nowTimeString(): String = LocalTime.now().format(TIME_FORMATTER)
}
