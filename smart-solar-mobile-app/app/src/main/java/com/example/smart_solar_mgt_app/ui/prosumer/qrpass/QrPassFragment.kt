package com.example.smart_solar_mgt_app.ui.prosumer.qrpass

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.applyEdgeToEdgeContentPadding
import com.example.smart_solar_mgt_app.di.ServiceLocator

/**
 * Read-only display of a APPROVED booking's Energy Transfer Pass. No scan/complete action lives
 * here - that's the Grid Operator's side (Phase 14), a different Activity entirely.
 */
class QrPassFragment : Fragment(R.layout.fragment_qr_pass) {

    private val bookingId: String? by lazy { arguments?.getString(ARG_BOOKING_ID) }

    private val viewModel: QrPassViewModel by viewModels {
        viewModelFactory {
            initializer {
                QrPassViewModel(
                    ServiceLocator.remoteTransactionRepository,
                    ServiceLocator.bookingRepository,
                    ServiceLocator.nodeRepository,
                    ServiceLocator.securityManager
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyEdgeToEdgeContentPadding()
        view.findViewById<View>(R.id.btnBack).setOnClickListener { findNavController().popBackStack() }

        val progress = view.findViewById<ProgressBar>(R.id.progressQrPass)
        val tvError = view.findViewById<TextView>(R.id.tvQrPassError)
        val group = view.findViewById<View>(R.id.groupQrPass)
        val imgQrCode = view.findViewById<ImageView>(R.id.imgQrCode)
        val tvStation = view.findViewById<TextView>(R.id.tvQrStation)
        val tvDateTime = view.findViewById<TextView>(R.id.tvQrDateTime)
        val tvEnergy = view.findViewById<TextView>(R.id.tvQrEnergy)
        val tvTransactionId = view.findViewById<TextView>(R.id.tvQrTransactionId)
        val tvExpiry = view.findViewById<TextView>(R.id.tvQrExpiry)

        viewModel.state.observe(viewLifecycleOwner) { state ->
            progress.isVisible = state is QrPassUiState.Loading
            group.isVisible = state is QrPassUiState.Loaded
            tvError.isVisible = state is QrPassUiState.Error

            when (state) {
                is QrPassUiState.Loaded -> {
                    imgQrCode.setImageBitmap(state.qrBitmap)
                    tvStation.text = state.stationName
                    tvDateTime.text = "${state.dateLabel} at ${state.timeLabel}"
                    tvEnergy.text = "${state.energyAmount} kWh"
                    tvTransactionId.text = "Transaction ID: ${state.transactionId}"
                    tvExpiry.text = "Valid until: ${state.expiryLabel}"
                }
                is QrPassUiState.Error -> tvError.text = state.message
                QrPassUiState.Loading -> Unit
            }
        }

        viewModel.load(bookingId.orEmpty())
    }

    companion object {
        const val ARG_BOOKING_ID = "bookingId"
    }
}
