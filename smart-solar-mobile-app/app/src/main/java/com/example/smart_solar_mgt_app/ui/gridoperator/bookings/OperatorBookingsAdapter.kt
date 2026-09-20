package com.example.smart_solar_mgt_app.ui.gridoperator.bookings

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.domain.model.BookingListItem
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.google.android.material.card.MaterialCardView

/** Read-only - no click action, unlike the Prosumer BookingsAdapter (there's no operator-facing booking detail screen). */
class OperatorBookingsAdapter : ListAdapter<BookingListItem, OperatorBookingsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_operator_booking, parent, false)
        return ViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        private val tvNic: TextView = card.findViewById(R.id.tvObNic)
        private val tvStation: TextView = card.findViewById(R.id.tvObStation)
        private val tvDateTime: TextView = card.findViewById(R.id.tvObDateTime)
        private val tvStatus: TextView = card.findViewById(R.id.tvObStatus)

        fun bind(item: BookingListItem) {
            tvNic.text = "NIC: ${item.prosumerNic}"
            tvStation.text = item.stationName
            tvDateTime.text = "${item.bookingDate} at ${item.bookingTime}  •  ${item.energyAmount} kWh"
            tvStatus.text = item.status.name
            val color = colorFor(item.status)
            tvStatus.setTextColor(color)
            tvStatus.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 38))
        }

        private fun colorFor(status: BookingStatus): Int = when (status) {
            BookingStatus.PENDING -> Color.parseColor("#F9A825")
            BookingStatus.APPROVED -> Color.parseColor("#2E7D32")
            BookingStatus.COMPLETED -> Color.parseColor("#1565C0")
            BookingStatus.CANCELLED -> Color.parseColor("#C62828")
            BookingStatus.REJECTED -> Color.parseColor("#E65100")
            BookingStatus.EXPIRED -> Color.parseColor("#757575")
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BookingListItem>() {
        override fun areItemsTheSame(oldItem: BookingListItem, newItem: BookingListItem) = oldItem.bookingId == newItem.bookingId
        override fun areContentsTheSame(oldItem: BookingListItem, newItem: BookingListItem) = oldItem == newItem
    }
}
