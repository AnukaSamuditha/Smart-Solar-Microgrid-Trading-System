package com.example.smart_solar_mgt_app.ui.prosumer.newbooking

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.applyEdgeToEdgeContentPadding
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.SolarStation
import com.example.smart_solar_mgt_app.ui.prosumer.bookingsummary.BookingActionSummaryFragment
import com.example.smart_solar_mgt_app.ui.prosumer.bookingsummary.BookingActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class NewBookingFragment : Fragment(R.layout.fragment_new_booking) {

    private val viewModel: NewBookingViewModel by viewModels {
        viewModelFactory {
            initializer {
                NewBookingViewModel(ServiceLocator.bookingRepository, ServiceLocator.stationRepository, ServiceLocator.securityManager)
            }
        }
    }

    private val preselectedStationId: String? by lazy { arguments?.getString(ARG_STATION_ID) }
    private val editBookingId: String? by lazy { arguments?.getString(ARG_BOOKING_ID) }

    private var editingBooking: Booking? = null
    private var selectedStationId: String? = null
    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null
    private var stationsById: Map<String, SolarStation> = emptyMap()

    private lateinit var tilStation: TextInputLayout
    private lateinit var tilDate: TextInputLayout
    private lateinit var tilTime: TextInputLayout
    private lateinit var tilEnergyAmount: TextInputLayout
    private lateinit var etStation: AutoCompleteTextView
    private lateinit var etDate: TextInputEditText
    private lateinit var etTime: TextInputEditText
    private lateinit var etEnergyAmount: TextInputEditText
    private lateinit var tvCapacityHint: android.widget.TextView
    private lateinit var btnConfirmBooking: MaterialButton
    private lateinit var progressNewBooking: ProgressBar

    private val fieldLayouts by lazy {
        mapOf(
            BookingField.STATION to tilStation,
            BookingField.DATE to tilDate,
            BookingField.TIME to tilTime,
            BookingField.ENERGY_AMOUNT to tilEnergyAmount
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyEdgeToEdgeContentPadding()

        view.findViewById<View>(R.id.btnBack).setOnClickListener { findNavController().popBackStack() }

        selectedStationId = preselectedStationId

        val isEditMode = editBookingId != null

        tilStation = view.findViewById(R.id.tilStation)
        tilDate = view.findViewById(R.id.tilDate)
        tilTime = view.findViewById(R.id.tilTime)
        tilEnergyAmount = view.findViewById(R.id.tilEnergyAmount)
        etStation = view.findViewById(R.id.etStation)
        etDate = view.findViewById(R.id.etDate)
        etTime = view.findViewById(R.id.etTime)
        etEnergyAmount = view.findViewById(R.id.etEnergyAmount)
        tvCapacityHint = view.findViewById(R.id.tvCapacityHint)
        btnConfirmBooking = view.findViewById(R.id.btnConfirmBooking)
        progressNewBooking = view.findViewById(R.id.progressNewBooking)

        etDate.setOnClickListener { showDatePicker() }
        etTime.setOnClickListener { showTimePicker() }

        if (isEditMode) {
            btnConfirmBooking.text = "Save Changes"
            view.findViewById<android.widget.TextView>(R.id.tvNewBookingTitle)?.text = "Edit Reservation"
        }

        btnConfirmBooking.setOnClickListener {
            fieldLayouts.values.forEach { it.error = null }
            val bookingId = editBookingId
            if (bookingId != null) {
                viewModel.onUpdateClicked(
                    bookingId,
                    selectedStationId,
                    selectedDate,
                    selectedTime,
                    etEnergyAmount.text?.toString().orEmpty()
                )
            } else {
                viewModel.onConfirmClicked(
                    selectedStationId,
                    selectedDate,
                    selectedTime,
                    etEnergyAmount.text?.toString().orEmpty()
                )
            }
        }

        viewModel.stations.observe(viewLifecycleOwner) { stations -> onStationsLoaded(stations) }
        viewModel.formState.observe(viewLifecycleOwner) { state -> render(state) }

        if (isEditMode) {
            loadBookingForEdit(editBookingId!!)
        } else {
            viewModel.loadStations(preselectedStationId)
        }
    }

    private fun loadBookingForEdit(bookingId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val booking = withContext(Dispatchers.IO) { ServiceLocator.bookingRepository.getBookingById(bookingId) }
            if (booking == null) {
                Toast.makeText(requireContext(), "This reservation is no longer available.", Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
                return@launch
            }
            editingBooking = booking
            selectedDate = LocalDate.parse(booking.bookingDate)
            selectedTime = LocalTime.parse(booking.bookingTime)
            etDate.setText(booking.bookingDate)
            etTime.setText(booking.bookingTime)
            etEnergyAmount.setText(booking.energyAmount.toString())
            viewModel.loadStations(booking.stationId)
        }
    }

    private fun onStationsLoaded(stations: List<SolarStation>) {
        stationsById = stations.associateBy { it.stationId }

        val lockedStationId = preselectedStationId ?: editingBooking?.stationId
        if (lockedStationId != null) {
            selectedStationId = lockedStationId
            val station = stationsById[lockedStationId]
            etStation.isEnabled = false
            if (station != null) {
                etStation.setText(stationLabel(station), false)
                updateCapacityHint(station)
            }
            return
        }

        val labels = stations.map { stationLabel(it) }
        etStation.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        etStation.setOnItemClickListener { _, _, position, _ ->
            val station = stations[position]
            selectedStationId = station.stationId
            updateCapacityHint(station)
        }
    }

    private fun stationLabel(station: SolarStation) = "${station.stationName} (${station.availableSlots} slots left)"

    private fun updateCapacityHint(station: SolarStation) {
        tvCapacityHint.text = "Max available at this station: ${station.capacityKwh} kWh"
    }

    private fun showDatePicker() {
        val zone = ZoneId.systemDefault()
        val todayMillis = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val maxMillis = LocalDate.now().plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val constraints = CalendarConstraints.Builder().setStart(todayMillis).setEnd(maxMillis).build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select reservation date")
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            selectedDate = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            etDate.setText(selectedDate.toString())
        }
        picker.show(childFragmentManager, "datePicker")
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText("Select reservation time")
            .build()
        picker.addOnPositiveButtonClickListener {
            selectedTime = LocalTime.of(picker.hour, picker.minute)
            etTime.setText("%02d:%02d".format(picker.hour, picker.minute))
        }
        picker.show(childFragmentManager, "timePicker")
    }

    private fun render(state: NewBookingFormState) {
        progressNewBooking.isVisible = state is NewBookingFormState.Saving
        btnConfirmBooking.isEnabled = state !is NewBookingFormState.Saving

        when (state) {
            is NewBookingFormState.FieldErrors -> state.errors.forEach { (field, message) ->
                fieldLayouts[field]?.error = message
            }
            is NewBookingFormState.FormError -> Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            is NewBookingFormState.Created -> navigateToSummary(state.booking.bookingId, BookingActionType.CREATED)
            is NewBookingFormState.Updated -> navigateToSummary(state.booking.bookingId, BookingActionType.UPDATED)
            NewBookingFormState.Idle, NewBookingFormState.Saving -> Unit
        }
    }

    private fun navigateToSummary(bookingId: String, actionType: BookingActionType) {
        val args = Bundle().apply {
            putString(BookingActionSummaryFragment.ARG_BOOKING_ID, bookingId)
            putString(BookingActionSummaryFragment.ARG_ACTION_TYPE, actionType.name)
        }
        findNavController().navigate(R.id.action_newBookingFragment_to_bookingActionSummaryFragment, args)
    }

    companion object {
        const val ARG_STATION_ID = "stationId"
        const val ARG_BOOKING_ID = "bookingId"
    }
}
