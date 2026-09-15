package com.example.smart_solar_mgt_app.ui.gridoperator.home

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class OperatorPendingAdapter(
    private val onApprove: (PendingApprovalItem) -> Unit,
    private val onReject: (PendingApprovalItem) -> Unit
) : ListAdapter<PendingApprovalItem, OperatorPendingAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_operator_pending_booking, parent, false)
        return ViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onApprove, onReject)
    }

    class ViewHolder(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        private val tvNic: TextView = card.findViewById(R.id.tvOpNic)
        private val tvStation: TextView = card.findViewById(R.id.tvOpStation)
        private val tvDateTime: TextView = card.findViewById(R.id.tvOpDateTime)
        private val tvEnergy: TextView = card.findViewById(R.id.tvOpEnergy)
        private val btnApprove: MaterialButton = card.findViewById(R.id.btnOpApprove)
        private val btnReject: MaterialButton = card.findViewById(R.id.btnOpReject)

        fun bind(item: PendingApprovalItem, onApprove: (PendingApprovalItem) -> Unit, onReject: (PendingApprovalItem) -> Unit) {
            tvNic.text = "NIC: ${item.prosumerNic}"
            tvStation.text = item.stationName
            tvDateTime.text = "${item.bookingDate} at ${item.bookingTime}"
            tvEnergy.text = "${item.energyAmount} kWh"
            btnApprove.setOnClickListener { onApprove(item) }
            btnReject.setOnClickListener { onReject(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PendingApprovalItem>() {
        override fun areItemsTheSame(oldItem: PendingApprovalItem, newItem: PendingApprovalItem) = oldItem.bookingId == newItem.bookingId
        override fun areContentsTheSame(oldItem: PendingApprovalItem, newItem: PendingApprovalItem) = oldItem == newItem
    }
}
