package com.example.smart_solar_mgt_app.ui.prosumer.newbooking

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.SolarStation
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

        selectedStationId = preselectedStationId

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

        btnConfirmBooking.setOnClickListener {
            fieldLayouts.values.forEach { it.error = null }
            viewModel.onConfirmClicked(
                selectedStationId,
                selectedDate,
                selectedTime,
                etEnergyAmount.text?.toString().orEmpty()
            )
        }

        viewModel.stations.observe(viewLifecycleOwner) { stations -> onStationsLoaded(stations) }
        viewModel.formState.observe(viewLifecycleOwner) { state -> render(state) }
        viewModel.loadStations(preselectedStationId)
    }

    private fun onStationsLoaded(stations: List<SolarStation>) {
        stationsById = stations.associateBy { it.stationId }

        if (preselectedStationId != null) {
            val station = stationsById[preselectedStationId]
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
            is NewBookingFormState.Created -> showSummary(state)
            NewBookingFormState.Idle, NewBookingFormState.Saving -> Unit
        }
    }

    private fun showSummary(state: NewBookingFormState.Created) {
        val message = "Station: ${state.station.stationName}\n" +
            "Date: ${state.booking.bookingDate}\n" +
            "Time: ${state.booking.bookingTime}\n" +
            "Energy: ${state.booking.energyAmount} kWh\n" +
            "Status: ${state.booking.status.name}"

        AlertDialog.Builder(requireContext())
            .setTitle("Reservation Created")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Done") { _, _ -> findNavController().popBackStack() }
            .show()
    }

    companion object {
        const val ARG_STATION_ID = "stationId"
    }
}
