package com.example.smart_solar_mgt_app.debug

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class DebugPendingAdapter(
    private val onApprove: (Booking) -> Unit,
    private val onReject: (Booking) -> Unit
) : ListAdapter<Booking, DebugPendingAdapter.ViewHolder>(DiffCallback) {

    private var stationNames: Map<String, String> = emptyMap()

    fun updateStationNames(names: Map<String, String>) {
        stationNames = names
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_debug_pending_booking, parent, false)
        return ViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), stationNames, onApprove, onReject)
    }

    class ViewHolder(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        private val tvNic: TextView = card.findViewById(R.id.tvDebugNic)
        private val tvStation: TextView = card.findViewById(R.id.tvDebugStation)
        private val tvDateTime: TextView = card.findViewById(R.id.tvDebugDateTime)
        private val tvEnergy: TextView = card.findViewById(R.id.tvDebugEnergy)
        private val btnApprove: MaterialButton = card.findViewById(R.id.btnDebugApprove)
        private val btnReject: MaterialButton = card.findViewById(R.id.btnDebugReject)

        fun bind(booking: Booking, stationNames: Map<String, String>, onApprove: (Booking) -> Unit, onReject: (Booking) -> Unit) {
            tvNic.text = "NIC: ${booking.prosumerNic}"
            tvStation.text = "Station: ${stationNames[booking.stationId] ?: booking.stationId}"
            tvDateTime.text = "${booking.bookingDate} at ${booking.bookingTime}"
            tvEnergy.text = "${booking.energyAmount} kWh"
            btnApprove.setOnClickListener { onApprove(booking) }
            btnReject.setOnClickListener { onReject(booking) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Booking>() {
        override fun areItemsTheSame(oldItem: Booking, newItem: Booking) = oldItem.bookingId == newItem.bookingId
        override fun areContentsTheSame(oldItem: Booking, newItem: Booking) = oldItem == newItem
    }
}
