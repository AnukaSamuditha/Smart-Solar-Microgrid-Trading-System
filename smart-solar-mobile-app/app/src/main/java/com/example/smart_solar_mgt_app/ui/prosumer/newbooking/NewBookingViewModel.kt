package com.example.smart_solar_mgt_app.ui.prosumer.newbooking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.data.repository.NodeRepository
import com.example.smart_solar_mgt_app.domain.model.MicrogridNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class NewBookingViewModel(
    private val bookingRepository: BookingRepository,
    private val nodeRepository: NodeRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _nodes = MutableLiveData<List<MicrogridNode>>(emptyList())
    val nodes: LiveData<List<MicrogridNode>> = _nodes

    private val _formState = MutableLiveData<NewBookingFormState>(NewBookingFormState.Idle)
    val formState: LiveData<NewBookingFormState> = _formState

    /** Refreshes the node/slot cache from the backend when online, then loads whatever is
     * cached (works offline too, showing the last-known list - see NodeRepository). */
    fun loadNodes() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                nodeRepository.refreshFromRemote()
                nodeRepository.getAllNodes()
            }
            _nodes.value = loaded
        }
    }

    fun onConfirmClicked(nodeId: String?, slotId: String?, date: LocalDate?, time: LocalTime?, energyAmountText: String) {
        val node = _nodes.value?.firstOrNull { it.nodeId == nodeId }
        val errors = BookingValidator.validate(
            BookingValidator.Input(nodeId, slotId, date, time, energyAmountText),
            node?.capacityKw
        )
        if (errors.isNotEmpty()) {
            _formState.value = NewBookingFormState.FieldErrors(errors)
            return
        }

        _formState.value = NewBookingFormState.Saving
        viewModelScope.launch {
            val nic = securityManager.currentSession()?.userId ?: return@launch
            val outcome = withContext(Dispatchers.IO) {
                createBooking(nic, nodeId!!, slotId!!, date!!, time!!, energyAmountText.trim().toDouble())
            }
            _formState.value = outcome
        }
    }

    fun onUpdateClicked(bookingId: String, nodeId: String?, slotId: String?, date: LocalDate?, time: LocalTime?, energyAmountText: String) {
        val node = _nodes.value?.firstOrNull { it.nodeId == nodeId }
        val errors = BookingValidator.validate(
            BookingValidator.Input(nodeId, slotId, date, time, energyAmountText),
            node?.capacityKw
        )
        if (errors.isNotEmpty()) {
            _formState.value = NewBookingFormState.FieldErrors(errors)
            return
        }

        _formState.value = NewBookingFormState.Saving
        viewModelScope.launch {
            val nic = securityManager.currentSession()?.userId ?: return@launch
            val outcome = withContext(Dispatchers.IO) {
                updateBooking(bookingId, nic, node, date!!, time!!)
            }
            _formState.value = outcome
        }
    }

    private fun updateBooking(
        bookingId: String,
        nic: String,
        node: MicrogridNode?,
        date: LocalDate,
        time: LocalTime
    ): NewBookingFormState {
        val result = bookingRepository.updateBooking(bookingId, nic, date, time)
        return when (result) {
            is AppResult.Success -> {
                val resolvedNode = node ?: nodeRepository.getNodeById(result.data.nodeId)
                if (resolvedNode != null) NewBookingFormState.Updated(result.data, resolvedNode) else NewBookingFormState.FormError("Could not update reservation. Please try again.")
            }
            is AppResult.Failure -> NewBookingFormState.FormError(
                when (result.error) {
                    AppError.TooLateToModify -> "This reservation can no longer be modified - less than 12 hours remain."
                    AppError.InvalidStatusTransition -> "This reservation has already been cancelled or completed and cannot be changed."
                    else -> "Could not update reservation. Please try again."
                }
            )
        }
    }

    private fun createBooking(
        nic: String,
        nodeId: String,
        slotId: String,
        date: LocalDate,
        time: LocalTime,
        energyAmount: Double
    ): NewBookingFormState {
        return when (val result = bookingRepository.createBooking(nic, nodeId, slotId, date, time, energyAmount)) {
            is AppResult.Success -> {
                val node = nodeRepository.getNodeById(nodeId)
                if (node != null) NewBookingFormState.Created(result.data, node) else NewBookingFormState.FormError("Reservation created, but could not load node details.")
            }
            is AppResult.Failure -> NewBookingFormState.FormError(
                when (val error = result.error) {
                    is AppError.Unknown -> error.message
                    else -> "This slot is no longer available. Please choose another."
                }
            )
        }
    }
}
