package com.example.smart_solar_mgt_app.ui.prosumer.bookings

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.ui.prosumer.newbooking.NewBookingFragment
import com.example.smart_solar_mgt_app.util.BookingTimeRules
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/**
 * Modify/Cancel actions are gated both here (UI, fast-fail) and again inside
 * LocalDbManager.updateBooking/cancelBooking (the actual race-safe check) - real time can
 * elapse between opening this screen and tapping a button.
 */
class BookingDetailFragment : Fragment(R.layout.fragment_booking_detail) {

    private val bookingId: String? by lazy { arguments?.getString(ARG_BOOKING_ID) }
    private var stationName: String = "Unknown station"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progress = view.findViewById<ProgressBar>(R.id.progressBookingDetail)
        val group = view.findViewById<View>(R.id.groupBookingDetail)
        val tvStation = view.findViewById<TextView>(R.id.tvDetailStation)
        val tvDateTime = view.findViewById<TextView>(R.id.tvDetailDateTime)
        val tvEnergy = view.findViewById<TextView>(R.id.tvDetailEnergy)
        val tvStatus = view.findViewById<TextView>(R.id.tvDetailStatus)
        val tvSyncStatus = view.findViewById<TextView>(R.id.tvDetailSyncStatus)
        val btnModify = view.findViewById<MaterialButton>(R.id.btnModify)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val tvNoticeHelper = view.findViewById<TextView>(R.id.tvNoticeHelper)

        val id = bookingId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val booking = withContext(Dispatchers.IO) { ServiceLocator.bookingRepository.getBookingById(id) }
            stationName = booking?.let {
                withContext(Dispatchers.IO) { ServiceLocator.stationRepository.getStationById(it.stationId)?.stationName }
            } ?: "Unknown station"

            progress.isVisible = false
            if (booking == null) return@launch

            group.isVisible = true
            tvStation.text = stationName
            tvDateTime.text = "${booking.bookingDate} at ${booking.bookingTime}"
            tvEnergy.text = "${booking.energyAmount} kWh"
            tvStatus.text = "Status: ${booking.status.name}"
            tvSyncStatus.text = "Sync: ${booking.syncStatus.name}"

            val isActiveStatus = booking.status == BookingStatus.PENDING || booking.status == BookingStatus.CONFIRMED
            val canAct = isActiveStatus && BookingTimeRules.canModifyOrCancel(
                booking.status,
                LocalDate.parse(booking.bookingDate),
                LocalTime.parse(booking.bookingTime)
            )

            btnModify.isVisible = isActiveStatus
            btnCancel.isVisible = isActiveStatus
            btnModify.isEnabled = canAct
            btnCancel.isEnabled = canAct
            tvNoticeHelper.isVisible = isActiveStatus && !canAct

            btnModify.setOnClickListener {
                val args = Bundle().apply { putString(NewBookingFragment.ARG_BOOKING_ID, booking.bookingId) }
                findNavController().navigate(R.id.action_bookingDetailFragment_to_newBookingFragment, args)
            }
            btnCancel.setOnClickListener { confirmCancel(booking) }
        }
    }

    private fun confirmCancel(booking: Booking) {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Reservation?")
            .setMessage("$stationName - ${booking.bookingDate} at ${booking.bookingTime}. This cannot be undone.")
            .setPositiveButton("Cancel Reservation") { _, _ -> performCancel(booking.bookingId) }
            .setNegativeButton("Keep Reservation", null)
            .show()
    }

    private fun performCancel(bookingId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val nic = ServiceLocator.securityManager.currentSession()?.userId ?: return@launch
            val result = withContext(Dispatchers.IO) { ServiceLocator.bookingRepository.cancelBooking(bookingId, nic) }
            when (result) {
                is AppResult.Success -> {
                    Toast.makeText(requireContext(), "Reservation cancelled", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                is AppResult.Failure -> {
                    val message = when (result.error) {
                        AppError.TooLateToModify -> "This reservation can no longer be cancelled - less than 12 hours remain."
                        AppError.InvalidStatusTransition -> "This reservation has already been cancelled or completed."
                        else -> "Could not cancel reservation. Please try again."
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val ARG_BOOKING_ID = "bookingId"
    }
}
