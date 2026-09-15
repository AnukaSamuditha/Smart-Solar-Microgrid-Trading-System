package com.example.smart_solar_mgt_app.util

import java.time.format.DateTimeFormatter

/** Shared formats so stored booking_date/booking_time strings stay consistently zero-padded
 * and lexicographically sortable (relied on by LocalDbManager's date/time queries). */
object DateFormats {
    val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}
