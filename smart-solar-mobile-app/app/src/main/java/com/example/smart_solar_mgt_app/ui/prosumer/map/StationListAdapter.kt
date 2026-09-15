package com.example.smart_solar_mgt_app.ui.prosumer.map

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.domain.model.SolarStation
import com.example.smart_solar_mgt_app.domain.model.StationStatus
import com.google.android.material.card.MaterialCardView

class StationListAdapter(
    private val onItemClick: (SolarStation) -> Unit
) : ListAdapter<SolarStation, StationListAdapter.ViewHolder>(DiffCallback) {

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

        fun bind(station: SolarStation, onItemClick: (SolarStation) -> Unit) {
            tvName.text = station.stationName
            tvCapacity.text = "Capacity: ${station.capacityKwh} kWh"
            tvSlots.text = "Available Slots: ${station.availableSlots}"
            tvStatus.text = station.status.name
            tvStatus.setTextColor(colorFor(station.status))
            card.setOnClickListener { onItemClick(station) }
        }

        private fun colorFor(status: StationStatus): Int = when (status) {
            StationStatus.ACTIVE -> Color.parseColor("#2E7D32")
            StationStatus.FULL -> Color.parseColor("#EF6C00")
            StationStatus.MAINTENANCE, StationStatus.OFFLINE -> Color.parseColor("#757575")
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SolarStation>() {
        override fun areItemsTheSame(oldItem: SolarStation, newItem: SolarStation) = oldItem.stationId == newItem.stationId
        override fun areContentsTheSame(oldItem: SolarStation, newItem: SolarStation) = oldItem == newItem
    }
}
