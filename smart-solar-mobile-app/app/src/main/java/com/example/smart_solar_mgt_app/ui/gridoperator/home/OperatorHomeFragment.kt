package com.example.smart_solar_mgt_app.ui.gridoperator.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.common.applyEdgeToEdgeContentPadding
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Real replacement for the Phase 11 debug approval tool - same repository methods, real UI. */
class OperatorHomeFragment : Fragment(R.layout.fragment_operator_home) {

    private val viewModel: OperatorHomeViewModel by viewModels {
        viewModelFactory {
            initializer { OperatorHomeViewModel(ServiceLocator.bookingRepository, ServiceLocator.stationRepository) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyEdgeToEdgeContentPadding()

        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshOpHome)
        val progress = view.findViewById<ProgressBar>(R.id.progressOpHome)
        val tvEmpty = view.findViewById<TextView>(R.id.tvOpHomeEmpty)
        val tvPendingCount = view.findViewById<TextView>(R.id.tvOpPendingCount)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvOpPending)
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnOpLogout)

        val adapter = OperatorPendingAdapter(
            onApprove = { item -> approve(item.bookingId) },
            onReject = { item -> reject(item.bookingId) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { viewModel.load() }
        btnLogout.setOnClickListener {
            ServiceLocator.securityManager.logout()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            requireActivity().finish()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            swipeRefresh.isRefreshing = false
            progress.isVisible = state is OperatorHomeUiState.Loading
            tvEmpty.isVisible = state is OperatorHomeUiState.Empty
            recyclerView.isVisible = state is OperatorHomeUiState.Loaded

            tvPendingCount.text = when (state) {
                is OperatorHomeUiState.Loaded -> state.items.size.toString()
                OperatorHomeUiState.Empty -> "0"
                OperatorHomeUiState.Loading -> "-"
            }

            if (state is OperatorHomeUiState.Loaded) {
                adapter.submitList(state.items)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun approve(bookingId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ServiceLocator.bookingRepository.approveBooking(bookingId) }
            when (result) {
                is AppResult.Success -> Toast.makeText(requireContext(), "Booking approved", Toast.LENGTH_SHORT).show()
                is AppResult.Failure -> Toast.makeText(requireContext(), "Approve failed: ${result.error}", Toast.LENGTH_LONG).show()
            }
            viewModel.load()
        }
    }

    private fun reject(bookingId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ServiceLocator.bookingRepository.rejectBooking(bookingId) }
            when (result) {
                is AppResult.Success -> Toast.makeText(requireContext(), "Booking rejected", Toast.LENGTH_SHORT).show()
                is AppResult.Failure -> Toast.makeText(requireContext(), "Reject failed: ${result.error}", Toast.LENGTH_LONG).show()
            }
            viewModel.load()
        }
    }
}
