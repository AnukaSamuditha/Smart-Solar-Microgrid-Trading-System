package com.example.smart_solar_mgt_app.ui.prosumer.bookings

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.core.common.SyncStatus
import com.example.smart_solar_mgt_app.domain.model.BookingListItem
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.google.android.material.card.MaterialCardView

class BookingsAdapter(
    private val onItemClick: (BookingListItem) -> Unit
) : ListAdapter<BookingListItem, BookingsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_booking, parent, false)
        return ViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class ViewHolder(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        private val tvStation: TextView = card.findViewById(R.id.tvBookingStation)
        private val tvDateTime: TextView = card.findViewById(R.id.tvBookingDateTime)
        private val tvEnergy: TextView = card.findViewById(R.id.tvBookingEnergy)
        private val tvStatus: TextView = card.findViewById(R.id.tvBookingStatus)
        private val ivSyncPending: ImageView = card.findViewById(R.id.ivSyncPending)

        fun bind(item: BookingListItem, onItemClick: (BookingListItem) -> Unit) {
            tvStation.text = item.stationName
            tvDateTime.text = "${item.bookingDate} at ${item.bookingTime}"
            tvEnergy.text = "${item.energyAmount} kWh"
            tvStatus.text = item.status.name
            tvStatus.setTextColor(colorFor(item.status))
            ivSyncPending.isVisible = item.syncStatus != SyncStatus.SYNCED
            card.setOnClickListener { onItemClick(item) }
        }

        private fun colorFor(status: BookingStatus): Int = when (status) {
            BookingStatus.PENDING -> Color.parseColor("#F9A825")
            BookingStatus.APPROVED -> Color.parseColor("#2E7D32")
            BookingStatus.COMPLETED -> Color.parseColor("#1565C0")
            BookingStatus.CANCELLED -> Color.parseColor("#C62828")
            BookingStatus.EXPIRED -> Color.parseColor("#757575")
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BookingListItem>() {
        override fun areItemsTheSame(oldItem: BookingListItem, newItem: BookingListItem) = oldItem.bookingId == newItem.bookingId
        override fun areContentsTheSame(oldItem: BookingListItem, newItem: BookingListItem) = oldItem == newItem
    }
}
