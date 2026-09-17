package com.example.smart_solar_mgt_app.ui.gridoperator.bookings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.applyEdgeToEdgeContentPadding
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.ui.prosumer.bookings.BookingScope
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText

class OperatorBookingsFragment : Fragment(R.layout.fragment_operator_bookings) {

    private val viewModel: OperatorBookingsViewModel by viewModels {
        viewModelFactory { initializer { OperatorBookingsViewModel(ServiceLocator.bookingRepository) } }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyEdgeToEdgeContentPadding()

        val etSearch = view.findViewById<TextInputEditText>(R.id.etOpSearch)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutOpBookings)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvOpBookings)
        val progress = view.findViewById<ProgressBar>(R.id.progressOpBookings)
        val tvEmpty = view.findViewById<TextView>(R.id.tvOpBookingsEmpty)

        val adapter = OperatorBookingsAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val scope = when (tab.position) {
                    0 -> BookingScope.CURRENT
                    1 -> BookingScope.PENDING
                    else -> BookingScope.HISTORY
                }
                viewModel.onScopeSelected(scope)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // Default to Pending - the actionable queue lives on Home, but an operator checking
        // Bookings is most often looking for what's still awaiting approval.
        tabLayout.getTabAt(1)?.select()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.onSearchChanged(s?.toString().orEmpty())
            }
        })

        viewModel.state.observe(viewLifecycleOwner) { state ->
            progress.isVisible = state is OperatorBookingsUiState.Loading
            recyclerView.isVisible = state is OperatorBookingsUiState.Loaded
            tvEmpty.isVisible = state is OperatorBookingsUiState.EmptyScope || state is OperatorBookingsUiState.NoResults

            when (state) {
                is OperatorBookingsUiState.Loaded -> adapter.submitList(state.items)
                OperatorBookingsUiState.EmptyScope -> tvEmpty.text = emptyScopeMessage(tabLayout.selectedTabPosition)
                OperatorBookingsUiState.NoResults -> tvEmpty.text = "No bookings match your search"
                OperatorBookingsUiState.Loading -> Unit
            }
        }
    }

    private fun emptyScopeMessage(tabPosition: Int): String = when (tabPosition) {
        0 -> "No confirmed reservations"
        1 -> "No pending reservations"
        else -> "No past reservations yet"
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}
