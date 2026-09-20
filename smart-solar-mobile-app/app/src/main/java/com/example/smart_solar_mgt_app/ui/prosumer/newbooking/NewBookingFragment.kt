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
import com.example.smart_solar_mgt_app.domain.model.BatterySlotStatus
import com.example.smart_solar_mgt_app.domain.model.MicrogridNode
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
                NewBookingViewModel(ServiceLocator.bookingRepository, ServiceLocator.nodeRepository, ServiceLocator.securityManager)
            }
        }
    }

    // Bundle key name kept as ARG_STATION_ID (an internal identifier, never shown to the user)
    // to avoid touching every existing caller - the value it carries is a node id.
    private val preselectedNodeId: String? by lazy { arguments?.getString(ARG_STATION_ID) }
    private val editBookingId: String? by lazy { arguments?.getString(ARG_BOOKING_ID) }

    private var editingBooking: Booking? = null
    private var selectedNodeId: String? = null
    private var selectedSlotId: String? = null
    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null
    private var nodesById: Map<String, MicrogridNode> = emptyMap()

    private lateinit var tilStation: TextInputLayout
    private lateinit var tilSlot: TextInputLayout
    private lateinit var tilDate: TextInputLayout
    private lateinit var tilTime: TextInputLayout
    private lateinit var tilEnergyAmount: TextInputLayout
    private lateinit var etStation: AutoCompleteTextView
    private lateinit var etSlot: AutoCompleteTextView
    private lateinit var etDate: TextInputEditText
    private lateinit var etTime: TextInputEditText
    private lateinit var etEnergyAmount: TextInputEditText
    private lateinit var tvCapacityHint: android.widget.TextView
    private lateinit var btnConfirmBooking: MaterialButton
    private lateinit var progressNewBooking: ProgressBar

    private val fieldLayouts by lazy {
        mapOf(
            BookingField.STATION to tilStation,
            BookingField.SLOT to tilSlot,
            BookingField.DATE to tilDate,
            BookingField.TIME to tilTime,
            BookingField.ENERGY_AMOUNT to tilEnergyAmount
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.applyEdgeToEdgeContentPadding()

        view.findViewById<View>(R.id.btnBack).setOnClickListener { findNavController().popBackStack() }

        selectedNodeId = preselectedNodeId

        val isEditMode = editBookingId != null

        tilStation = view.findViewById(R.id.tilStation)
        tilSlot = view.findViewById(R.id.tilSlot)
        tilDate = view.findViewById(R.id.tilDate)
        tilTime = view.findViewById(R.id.tilTime)
        tilEnergyAmount = view.findViewById(R.id.tilEnergyAmount)
        etStation = view.findViewById(R.id.etStation)
        etSlot = view.findViewById(R.id.etSlot)
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
            // The backend's reschedule endpoint only accepts a new time window - node/slot/energy
            // amount stay fixed (see BookingRepository.updateBooking) - so this field is locked
            // to whatever was originally booked rather than silently ignoring an edit to it.
            etEnergyAmount.isEnabled = false
        }

        btnConfirmBooking.setOnClickListener {
            fieldLayouts.values.forEach { it.error = null }
            val bookingId = editBookingId
            if (bookingId != null) {
                viewModel.onUpdateClicked(
                    bookingId,
                    selectedNodeId,
                    selectedSlotId,
                    selectedDate,
                    selectedTime,
                    etEnergyAmount.text?.toString().orEmpty()
                )
            } else {
                viewModel.onConfirmClicked(
                    selectedNodeId,
                    selectedSlotId,
                    selectedDate,
                    selectedTime,
                    etEnergyAmount.text?.toString().orEmpty()
                )
            }
        }

        viewModel.nodes.observe(viewLifecycleOwner) { nodes -> onNodesLoaded(nodes) }
        viewModel.formState.observe(viewLifecycleOwner) { state -> render(state) }

        if (isEditMode) {
            loadBookingForEdit(editBookingId!!)
        } else {
            viewModel.loadNodes()
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
            selectedSlotId = booking.slotId
            selectedDate = LocalDate.parse(booking.bookingDate)
            selectedTime = LocalTime.parse(booking.bookingTime)
            etDate.setText(booking.bookingDate)
            etTime.setText(booking.bookingTime)
            etEnergyAmount.setText(booking.energyAmount.toString())
            viewModel.loadNodes()
        }
    }

    private fun onNodesLoaded(nodes: List<MicrogridNode>) {
        nodesById = nodes.associateBy { it.nodeId }

        val lockedNodeId = preselectedNodeId ?: editingBooking?.nodeId
        if (lockedNodeId != null) {
            selectedNodeId = lockedNodeId
            val node = nodesById[lockedNodeId]
            etStation.isEnabled = false
            etSlot.isEnabled = false
            if (node != null) {
                etStation.setText(nodeLabel(node), false)
                updateCapacityHint(node)
            }
            etSlot.setText(slotLabel(selectedSlotId), false)
            return
        }

        val labels = nodes.map { nodeLabel(it) }
        etStation.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        etStation.setOnItemClickListener { _, _, position, _ ->
            val node = nodes[position]
            selectedNodeId = node.nodeId
            selectedSlotId = null
            etSlot.setText("", false)
            updateCapacityHint(node)
            populateSlotDropdown(node)
        }
    }

    private fun populateSlotDropdown(node: MicrogridNode) {
        val availableSlots = node.batterySlots.filter { it.status == BatterySlotStatus.AVAILABLE }
        val labels = availableSlots.map { slotLabel(it.slotId) }
        etSlot.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        etSlot.setOnItemClickListener { _, _, position, _ ->
            selectedSlotId = availableSlots[position].slotId
        }
    }

    private fun nodeLabel(node: MicrogridNode): String {
        val availableCount = node.batterySlots.count { it.status == BatterySlotStatus.AVAILABLE }
        return "${node.name} ($availableCount slots left)"
    }

    private fun slotLabel(slotId: String?): String = if (slotId == null) "" else "Slot $slotId"

    private fun updateCapacityHint(node: MicrogridNode) {
        tvCapacityHint.text = "Max available at this station: ${node.capacityKw} kWh"
        tvCapacityHint.isVisible = true
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
