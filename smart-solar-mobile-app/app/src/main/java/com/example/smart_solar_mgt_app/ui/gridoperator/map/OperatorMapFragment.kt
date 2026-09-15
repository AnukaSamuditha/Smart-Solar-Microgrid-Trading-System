package com.example.smart_solar_mgt_app.ui.gridoperator.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.SolarStation
import com.example.smart_solar_mgt_app.ui.prosumer.map.MapUiState
import com.example.smart_solar_mgt_app.ui.prosumer.map.MapViewModel
import com.example.smart_solar_mgt_app.ui.prosumer.map.StationListAdapter
import com.example.smart_solar_mgt_app.ui.prosumer.map.StationMarkerIcons
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton

/**
 * Read-only station map for the Grid Operator - same MapViewModel/StationListAdapter/
 * StationMarkerIcons as the Prosumer's tab (pure display, no role-specific logic there), but
 * the station detail dialog has no "Book Here" action since operators don't make reservations.
 */
class OperatorMapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    private val viewModel: MapViewModel by viewModels {
        viewModelFactory { initializer { MapViewModel(ServiceLocator.stationRepository) } }
    }

    private var googleMap: GoogleMap? = null
    private var stationById: Map<String, SolarStation> = emptyMap()
    private var pendingStations: List<SolarStation>? = null
    private var showingList = false

    private lateinit var mapPane: View
    private lateinit var swipeRefreshMap: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressMap: ProgressBar
    private lateinit var tvEmptyMap: TextView
    private lateinit var tvMapBanner: TextView
    private lateinit var btnToggleView: MaterialButton
    private lateinit var adapter: StationListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapPane = view.findViewById(R.id.mapPane)
        swipeRefreshMap = view.findViewById(R.id.swipeRefreshMap)
        recyclerView = view.findViewById(R.id.rvStations)
        progressMap = view.findViewById(R.id.progressMap)
        tvEmptyMap = view.findViewById(R.id.tvEmptyMap)
        tvMapBanner = view.findViewById(R.id.tvMapBanner)
        btnToggleView = view.findViewById(R.id.btnToggleView)

        adapter = StationListAdapter { station -> showStationDetail(station) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        swipeRefreshMap.setOnRefreshListener { viewModel.loadStations() }

        val playServicesAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(requireContext()) == com.google.android.gms.common.ConnectionResult.SUCCESS

        if (playServicesAvailable) {
            val mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer) as com.google.android.gms.maps.SupportMapFragment
            mapFragment.getMapAsync(this)
            btnToggleView.setOnClickListener { toggleView() }
        } else {
            showingList = true
            mapPane.isVisible = false
            swipeRefreshMap.isVisible = true
            btnToggleView.isVisible = false
        }

        tvMapBanner.isVisible = !isOnline()

        viewModel.state.observe(viewLifecycleOwner) { state ->
            swipeRefreshMap.isRefreshing = false
            progressMap.isVisible = state is MapUiState.Loading
            tvEmptyMap.isVisible = state is MapUiState.Empty

            if (state is MapUiState.Loaded) {
                stationById = state.stations.associateBy { it.stationId }
                adapter.submitList(state.stations)
                renderMarkers(state.stations)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tvMapBanner.isVisible = !isOnline()
        viewModel.loadStations()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        pendingStations?.let { renderMarkers(it) }
    }

    private fun renderMarkers(stations: List<SolarStation>) {
        val map = googleMap ?: run { pendingStations = stations; return }
        map.clear()
        for (station in stations) {
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(station.latitude, station.longitude))
                    .title(station.stationName)
                    .icon(BitmapDescriptorFactory.defaultMarker(StationMarkerIcons.hueFor(station.status)))
            )?.tag = station.stationId
        }
        map.setOnMarkerClickListener { marker ->
            val stationId = marker.tag as? String
            val station = stationId?.let { stationById[it] }
            if (station != null) {
                showStationDetail(station)
                true
            } else {
                false
            }
        }

        if (stations.isNotEmpty()) {
            try {
                if (stations.size == 1) {
                    val only = stations.first()
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(only.latitude, only.longitude), 12f))
                } else {
                    val bounds = LatLngBounds.Builder().apply {
                        stations.forEach { include(LatLng(it.latitude, it.longitude)) }
                    }.build()
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            } catch (e: IllegalStateException) {
                // Map view not laid out yet - safe to skip, markers are still plotted.
            }
        }
    }

    private fun toggleView() {
        showingList = !showingList
        mapPane.isVisible = !showingList
        swipeRefreshMap.isVisible = showingList
        btnToggleView.text = if (showingList) "Map View" else "List View"
    }

    private fun isOnline(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showStationDetail(station: SolarStation) {
        val message = buildString {
            append("Capacity: ${station.capacityKwh} kWh\n")
            append("Available Slots: ${station.availableSlots}\n")
            append("Status: ${station.status.name}\n")
            append("Location: ${station.latitude}, ${station.longitude}")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(station.stationName)
            .setMessage(message)
            .setNegativeButton("Close", null)
            .show()
    }
}
