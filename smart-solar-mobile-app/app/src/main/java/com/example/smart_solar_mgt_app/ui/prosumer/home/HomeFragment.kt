package com.example.smart_solar_mgt_app.ui.prosumer.home

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.ui.prosumer.qrpass.QrPassFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel(ServiceLocator.bookingRepository, ServiceLocator.stationRepository, ServiceLocator.securityManager)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshHome)
        val progressHome = view.findViewById<ProgressBar>(R.id.progressHome)
        val groupContent = view.findViewById<View>(R.id.groupContent)
        val emptyStateHome = view.findViewById<View>(R.id.emptyStateHome)
        val tvWelcome = view.findViewById<TextView>(R.id.tvWelcome)
        val tvPendingCount = view.findViewById<TextView>(R.id.tvPendingCount)
        val tvApprovedCount = view.findViewById<TextView>(R.id.tvApprovedCount)
        val tvCompletedCount = view.findViewById<TextView>(R.id.tvCompletedCount)
        val cardUpcoming = view.findViewById<MaterialCardView>(R.id.cardUpcoming)
        val tvNoUpcoming = view.findViewById<TextView>(R.id.tvNoUpcoming)
        val tvUpcomingStation = view.findViewById<TextView>(R.id.tvUpcomingStation)
        val tvUpcomingDateTime = view.findViewById<TextView>(R.id.tvUpcomingDateTime)
        val tvUpcomingEnergy = view.findViewById<TextView>(R.id.tvUpcomingEnergy)
        val tvUpcomingStatus = view.findViewById<TextView>(R.id.tvUpcomingStatus)
        val btnViewQr = view.findViewById<MaterialButton>(R.id.btnViewQr)
        val btnNewBooking = view.findViewById<MaterialButton>(R.id.btnNewBooking)
        val btnNewBookingEmpty = view.findViewById<MaterialButton>(R.id.btnNewBookingEmpty)

        val goToNewBooking = { findNavController().navigate(R.id.action_global_newBookingFragment) }
        btnNewBooking.setOnClickListener { goToNewBooking() }
        btnNewBookingEmpty.setOnClickListener { goToNewBooking() }

        swipeRefresh.setOnRefreshListener { viewModel.loadDashboard() }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            swipeRefresh.isRefreshing = false
            progressHome.isVisible = state is HomeUiState.Loading
            groupContent.isVisible = state is HomeUiState.Loaded
            emptyStateHome.isVisible = state is HomeUiState.Empty

            if (state is HomeUiState.Loaded) {
                tvWelcome.text = "Welcome, ${state.welcomeName}"
                tvPendingCount.text = state.counts.pending.toString()
                tvApprovedCount.text = state.counts.approved.toString()
                tvCompletedCount.text = state.counts.completed.toString()

                val upcoming = state.upcoming
                cardUpcoming.isVisible = upcoming != null
                tvNoUpcoming.isVisible = upcoming == null
                if (upcoming != null) {
                    tvUpcomingStation.text = upcoming.stationName
                    tvUpcomingDateTime.text = "${upcoming.dateLabel} at ${upcoming.timeLabel}"
                    tvUpcomingEnergy.text = "${upcoming.energyAmount} kWh"
                    tvUpcomingStatus.text = "Status: ${upcoming.status.name}"
                    btnViewQr.isVisible = upcoming.status == BookingStatus.APPROVED
                    btnViewQr.setOnClickListener {
                        val args = Bundle().apply { putString(QrPassFragment.ARG_BOOKING_ID, upcoming.bookingId) }
                        findNavController().navigate(R.id.action_global_qrPassFragment, args)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDashboard()
    }
}
