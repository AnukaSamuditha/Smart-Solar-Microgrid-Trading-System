package com.example.smart_solar_mgt_app.ui.gridoperator.prosumers

import android.os.Bundle
import android.view.View
import android.widget.EditText
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
import com.example.smart_solar_mgt_app.core.common.applyEdgeToEdgeContentPadding
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.OutboxOperationType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Grid Operator review queue for self-registered prosumer accounts (PendingApproval), mirroring
 * OperatorHomeFragment's pending-bookings-queue pattern - see project spec section 6.
 *
 * Approve/deny are local-first: the action is queued to the sync outbox and the item disappears
 * from this screen immediately, whether or not there's connectivity right now - see
 * core/sync/SyncManager.kt. */
class PendingProsumersFragment : Fragment(R.layout.fragment_pending_prosumers) {

    private val viewModel: PendingProsumersViewModel by viewModels {
        viewModelFactory { initializer { PendingProsumersViewModel(ServiceLocator.remoteProsumerRepository) } }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyEdgeToEdgeContentPadding()

        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshPp)
        val progress = view.findViewById<ProgressBar>(R.id.progressPp)
        val tvEmpty = view.findViewById<TextView>(R.id.tvPpEmpty)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvPpPending)

        val adapter = PendingProsumersAdapter(
            onApprove = { item -> confirmApprove(item) },
            onDeny = { item -> promptDeny(item) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            swipeRefresh.isRefreshing = false
            progress.isVisible = state is PendingProsumersUiState.Loading
            tvEmpty.isVisible = state is PendingProsumersUiState.Empty
            recyclerView.isVisible = state is PendingProsumersUiState.Loaded

            when (state) {
                is PendingProsumersUiState.Loaded -> adapter.submitList(state.items)
                is PendingProsumersUiState.Error -> Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                else -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun confirmApprove(item: PendingProsumerItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Approve Request?")
            .setMessage("${item.fullName ?: item.nic} will be emailed a code to set their password in the app.")
            .setPositiveButton("Approve") { _, _ -> approve(item) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptDeny(item: PendingProsumerItem) {
        val input = EditText(requireContext()).apply { hint = "Reason (optional)" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Deny Request?")
            .setView(input)
            .setPositiveButton("Deny") { _, _ -> deny(item, input.text?.toString()?.trim()?.ifEmpty { null }) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun approve(item: PendingProsumerItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ServiceLocator.localDbManager.enqueueOutboxOperation(OutboxOperationType.PROSUMER_APPROVE, item.nic, "{}")
            }
            ServiceLocator.syncManager.scheduleImmediateSync()
            Toast.makeText(requireContext(), "Approval queued", Toast.LENGTH_SHORT).show()
            viewModel.removeLocally(item.nic)
        }
    }

    private fun deny(item: PendingProsumerItem, reason: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val payloadJson = JSONObject().put("reason", reason).toString()
            withContext(Dispatchers.IO) {
                ServiceLocator.localDbManager.enqueueOutboxOperation(OutboxOperationType.PROSUMER_DENY, item.nic, payloadJson)
            }
            ServiceLocator.syncManager.scheduleImmediateSync()
            Toast.makeText(requireContext(), "Denial queued", Toast.LENGTH_SHORT).show()
            viewModel.removeLocally(item.nic)
        }
    }
}
