package com.example.smart_solar_mgt_app.ui.prosumer.bookingsummary

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.google.android.material.button.MaterialButton

/**
 * Terminal confirmation screen shown after a successful create/modify/cancel - never reachable
 * on a Failure (NewBookingViewModel only emits Created/Updated from AppResult.Success, and
 * BookingDetailFragment only navigates here from cancel's Success branch). Re-fetches the
 * booking by id via BookingActionSummaryViewModel rather than trusting anything passed through
 * nav args, so this always reflects what was actually committed.
 */
class BookingActionSummaryFragment : Fragment(R.layout.fragment_booking_action_summary) {

    private val bookingId: String? by lazy { arguments?.getString(ARG_BOOKING_ID) }
    private val actionType: BookingActionType by lazy {
        BookingActionType.valueOf(arguments?.getString(ARG_ACTION_TYPE) ?: BookingActionType.CREATED.name)
    }

    private val viewModel: BookingActionSummaryViewModel by viewModels {
        viewModelFactory {
            initializer { BookingActionSummaryViewModel(ServiceLocator.bookingRepository, ServiceLocator.stationRepository) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progress = view.findViewById<ProgressBar>(R.id.progressActionSummary)
        val tvError = view.findViewById<TextView>(R.id.tvActionSummaryError)
        val group = view.findViewById<View>(R.id.groupActionSummary)
        val tvHeadline = view.findViewById<TextView>(R.id.tvActionHeadline)
        val tvStation = view.findViewById<TextView>(R.id.tvSummaryStation)
        val tvDateTime = view.findViewById<TextView>(R.id.tvSummaryDateTime)
        val tvEnergy = view.findViewById<TextView>(R.id.tvSummaryEnergy)
        val tvBookingId = view.findViewById<TextView>(R.id.tvSummaryBookingId)
        val tvStatus = view.findViewById<TextView>(R.id.tvSummaryStatus)
        val tvSyncStatus = view.findViewById<TextView>(R.id.tvSummarySyncStatus)
        val btnBackToBookings = view.findViewById<MaterialButton>(R.id.btnBackToBookings)
        val actionIconContainer = view.findViewById<FrameLayout>(R.id.actionIconContainer)
        val ivActionIcon = view.findViewById<ImageView>(R.id.ivActionIcon)

        btnBackToBookings.setOnClickListener {
            findNavController().navigate(
                R.id.bookingsFragment,
                null,
                navOptions { popUpTo(R.id.bookingsFragment) { inclusive = false } }
            )
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            progress.isVisible = state is BookingActionSummaryUiState.Loading
            group.isVisible = state is BookingActionSummaryUiState.Loaded
            tvError.isVisible = state is BookingActionSummaryUiState.Error
            btnBackToBookings.isVisible = state !is BookingActionSummaryUiState.Loading

            when (state) {
                is BookingActionSummaryUiState.Loaded -> {
                    val booking = state.booking
                    tvHeadline.text = headlineFor(state.actionType)
                    styleActionIcon(state.actionType, actionIconContainer, ivActionIcon)
                    tvStation.text = state.stationName
                    tvDateTime.text = "${booking.bookingDate} at ${booking.bookingTime}"
                    tvEnergy.text = "${booking.energyAmount} kWh"
                    tvBookingId.text = "Booking ID: ${booking.bookingId}"
                    tvStatus.text = booking.status.name
                    tvStatus.setTextColor(colorFor(booking.status))
                    tvSyncStatus.isVisible = booking.syncStatus.name != "SYNCED"
                    tvSyncStatus.text = "Sync: ${booking.syncStatus.name}"
                }
                is BookingActionSummaryUiState.Error -> tvError.text = state.message
                BookingActionSummaryUiState.Loading -> Unit
            }
        }

        viewModel.load(bookingId.orEmpty(), actionType)
    }

    private fun styleActionIcon(actionType: BookingActionType, container: FrameLayout, icon: ImageView) {
        val background = container.background.mutate()
        when (actionType) {
            BookingActionType.CANCELLED -> {
                (background as? GradientDrawable)?.setColor(ContextCompat.getColor(requireContext(), R.color.status_cancelled))
                icon.setImageResource(R.drawable.ic_cancel_circle)
                icon.setColorFilter(Color.WHITE)
            }
            BookingActionType.CREATED, BookingActionType.UPDATED -> {
                (background as? GradientDrawable)?.setColor(ContextCompat.getColor(requireContext(), R.color.status_approved))
                icon.setImageResource(R.drawable.ic_approved)
                icon.setColorFilter(Color.WHITE)
            }
        }
    }

    private fun headlineFor(actionType: BookingActionType): String = when (actionType) {
        BookingActionType.CREATED -> "Reservation Created"
        BookingActionType.UPDATED -> "Reservation Updated"
        BookingActionType.CANCELLED -> "Reservation Cancelled"
    }

    private fun colorFor(status: BookingStatus): Int = when (status) {
        BookingStatus.PENDING -> Color.parseColor("#F9A825")
        BookingStatus.APPROVED -> Color.parseColor("#2E7D32")
        BookingStatus.COMPLETED -> Color.parseColor("#1565C0")
        BookingStatus.CANCELLED -> Color.parseColor("#C62828")
        BookingStatus.EXPIRED -> Color.parseColor("#757575")
    }

    companion object {
        const val ARG_BOOKING_ID = "bookingId"
        const val ARG_ACTION_TYPE = "actionType"
    }
}
