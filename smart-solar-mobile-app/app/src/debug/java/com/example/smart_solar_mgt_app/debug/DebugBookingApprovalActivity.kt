package com.example.smart_solar_mgt_app.debug

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.security.UnauthorizedAccessException
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.Booking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DEBUG ONLY - not part of any release build (see app/src/debug/). Calls the exact same
 * BookingRepository.approveBooking/rejectBooking/getAllPendingBookings methods the real Grid
 * Operator UI (ui/gridoperator) uses - this is a placeholder UI for that role, not a bypass of
 * it. Requires the developer to already be logged in as the seeded GRID_OPERATOR test account
 * via the normal Login screen; there is no login flow here.
 */
class DebugBookingApprovalActivity : AppCompatActivity() {

    private lateinit var adapter: DebugPendingAdapter
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_booking_approval)

        tvEmpty = findViewById(R.id.tvDebugEmpty)
        val recyclerView = findViewById<RecyclerView>(R.id.rvDebugPending)
        adapter = DebugPendingAdapter(
            onApprove = { booking -> approve(booking) },
            onReject = { booking -> reject(booking) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadPending()
    }

    private fun loadPending() {
        lifecycleScope.launch {
            try {
                val (pending, stationNames) = withContext(Dispatchers.IO) {
                    val bookings = ServiceLocator.bookingRepository.getAllPendingBookings()
                    val names = ServiceLocator.stationRepository.getAllStations().associate { it.stationId to it.stationName }
                    bookings to names
                }
                adapter.updateStationNames(stationNames)
                adapter.submitList(pending)
                tvEmpty.isVisible = pending.isEmpty()
            } catch (e: UnauthorizedAccessException) {
                Toast.makeText(
                    this@DebugBookingApprovalActivity,
                    "Log in as the seeded Grid Operator account (via the normal Login screen) first.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun approve(booking: Booking) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ServiceLocator.bookingRepository.approveBooking(booking.bookingId) }
            when (result) {
                is AppResult.Success -> {
                    Toast.makeText(this@DebugBookingApprovalActivity, "Approved ${result.data.bookingId}", Toast.LENGTH_SHORT).show()
                    loadPending()
                }
                is AppResult.Failure -> Toast.makeText(this@DebugBookingApprovalActivity, "Approve failed: ${result.error}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun reject(booking: Booking) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ServiceLocator.bookingRepository.rejectBooking(booking.bookingId) }
            when (result) {
                is AppResult.Success -> {
                    Toast.makeText(this@DebugBookingApprovalActivity, "Rejected ${booking.bookingId}", Toast.LENGTH_SHORT).show()
                    loadPending()
                }
                is AppResult.Failure -> Toast.makeText(this@DebugBookingApprovalActivity, "Reject failed: ${result.error}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
