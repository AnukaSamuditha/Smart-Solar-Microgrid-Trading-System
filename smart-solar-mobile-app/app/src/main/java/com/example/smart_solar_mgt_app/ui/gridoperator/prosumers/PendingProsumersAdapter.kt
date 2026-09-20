package com.example.smart_solar_mgt_app.ui.gridoperator.prosumers

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class PendingProsumersAdapter(
    private val onApprove: (PendingProsumerItem) -> Unit,
    private val onDeny: (PendingProsumerItem) -> Unit
) : ListAdapter<PendingProsumerItem, PendingProsumersAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pending_prosumer, parent, false)
        return ViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onApprove, onDeny)
    }

    class ViewHolder(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        private val tvName: TextView = card.findViewById(R.id.tvPpName)
        private val tvNic: TextView = card.findViewById(R.id.tvPpNic)
        private val tvContact: TextView = card.findViewById(R.id.tvPpContact)
        private val tvAddress: TextView = card.findViewById(R.id.tvPpAddress)
        private val btnApprove: MaterialButton = card.findViewById(R.id.btnPpApprove)
        private val btnDeny: MaterialButton = card.findViewById(R.id.btnPpDeny)

        fun bind(item: PendingProsumerItem, onApprove: (PendingProsumerItem) -> Unit, onDeny: (PendingProsumerItem) -> Unit) {
            tvName.text = item.fullName ?: "(no name provided)"
            tvNic.text = "NIC: ${item.nic}"
            tvContact.text = listOfNotNull(item.email, item.phone).joinToString("  •  ")
            tvAddress.text = item.address
            btnApprove.setOnClickListener { onApprove(item) }
            btnDeny.setOnClickListener { onDeny(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PendingProsumerItem>() {
        override fun areItemsTheSame(oldItem: PendingProsumerItem, newItem: PendingProsumerItem) = oldItem.nic == newItem.nic
        override fun areContentsTheSame(oldItem: PendingProsumerItem, newItem: PendingProsumerItem) = oldItem == newItem
    }
}
