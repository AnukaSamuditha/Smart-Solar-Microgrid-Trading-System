package com.example.smart_solar_mgt_app.ui.prosumer.bookings

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BookingsFragment : Fragment(R.layout.fragment_bookings) {

    private val viewModel: BookingsViewModel by viewModels {
        viewModelFactory {
            initializer { BookingsViewModel(ServiceLocator.bookingRepository, ServiceLocator.securityManager) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etSearch = view.findViewById<TextInputEditText>(R.id.etSearch)
        val etDateFilter = view.findViewById<TextInputEditText>(R.id.etDateFilter)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutBookings)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvBookings)
        val progressBookings = view.findViewById<ProgressBar>(R.id.progressBookings)
        val emptyState = view.findViewById<View>(R.id.emptyStateBookings)
        val tvEmptyMessage = view.findViewById<TextView>(R.id.tvEmptyBookingsMessage)
        val btnClearFilters = view.findViewById<MaterialButton>(R.id.btnClearFilters)

        val adapter = BookingsAdapter { item ->
            val args = Bundle().apply { putString(BookingDetailFragment.ARG_BOOKING_ID, item.bookingId) }
            findNavController().navigate(R.id.action_bookingsFragment_to_bookingDetailFragment, args)
        }
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

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.onSearchChanged(s?.toString().orEmpty())
            }
        })

        etDateFilter.setOnClickListener { showDateFilterPicker(etDateFilter) }
        etDateFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                if (text.isBlank()) {
                    viewModel.onDateFilterCleared()
                } else {
                    viewModel.onDateFilterSelected(LocalDate.parse(text))
                }
            }
        })

        btnClearFilters.setOnClickListener {
            etSearch.setText("")
            etDateFilter.setText("")
            viewModel.onFiltersCleared()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            progressBookings.isVisible = state is BookingsUiState.Loading
            recyclerView.isVisible = state is BookingsUiState.Loaded
            emptyState.isVisible = state is BookingsUiState.EmptyScope || state is BookingsUiState.NoResults

            when (state) {
                is BookingsUiState.Loaded -> adapter.submitList(state.items)
                BookingsUiState.EmptyScope -> {
                    tvEmptyMessage.text = emptyScopeMessage(tabLayout.selectedTabPosition)
                    btnClearFilters.isVisible = false
                }
                BookingsUiState.NoResults -> {
                    tvEmptyMessage.text = "No reservations match your filters"
                    btnClearFilters.isVisible = true
                }
                BookingsUiState.Loading -> Unit
            }
        }
    }

    private fun showDateFilterPicker(target: TextInputEditText) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Filter by date")
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            val date = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            target.setText(date.toString())
        }
        picker.show(childFragmentManager, "bookingDateFilterPicker")
    }

    private fun emptyScopeMessage(tabPosition: Int): String = when (tabPosition) {
        0 -> "You don't have any current reservations"
        1 -> "You don't have any pending reservations"
        else -> "You don't have any past reservations yet"
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}
