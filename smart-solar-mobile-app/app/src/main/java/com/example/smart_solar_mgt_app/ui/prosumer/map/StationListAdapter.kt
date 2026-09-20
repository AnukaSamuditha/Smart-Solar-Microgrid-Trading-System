package com.example.smart_solar_mgt_app.ui.prosumer.map

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
import com.example.smart_solar_mgt_app.domain.model.BatterySlotStatus
import com.example.smart_solar_mgt_app.domain.model.MicrogridNode
import com.example.smart_solar_mgt_app.domain.model.NodeStatus
import com.google.android.material.card.MaterialCardView

class StationListAdapter(
    private val onItemClick: (MicrogridNode) -> Unit
) : ListAdapter<MicrogridNode, StationListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false)
        return ViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class ViewHolder(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        private val tvName: TextView = card.findViewById(R.id.tvStationName)
        private val tvCapacity: TextView = card.findViewById(R.id.tvStationCapacity)
        private val tvSlots: TextView = card.findViewById(R.id.tvStationSlots)
        private val tvStatus: TextView = card.findViewById(R.id.tvStationStatus)

        fun bind(node: MicrogridNode, onItemClick: (MicrogridNode) -> Unit) {
            val availableCount = node.batterySlots.count { it.status == BatterySlotStatus.AVAILABLE }
            tvName.text = node.name
            tvCapacity.text = "${node.capacityKw} kWh"
            tvSlots.text = "$availableCount of ${node.batterySlots.size} slots left"
            tvStatus.text = node.status.name
            val color = colorFor(node.status)
            tvStatus.setTextColor(color)
            tvStatus.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 38))
            card.setOnClickListener { onItemClick(node) }
        }

        private fun colorFor(status: NodeStatus): Int = when (status) {
            NodeStatus.ACTIVE -> Color.parseColor("#2E7D32")
            NodeStatus.DEACTIVATED -> Color.parseColor("#757575")
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MicrogridNode>() {
        override fun areItemsTheSame(oldItem: MicrogridNode, newItem: MicrogridNode) = oldItem.nodeId == newItem.nodeId
        override fun areContentsTheSame(oldItem: MicrogridNode, newItem: MicrogridNode) = oldItem == newItem
    }
}
