package com.example.smart_solar_mgt_app.ui.gridoperator.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
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
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Read-only station map for the Grid Operator - same MapViewModel/StationListAdapter/
 * StationMarkerIcons as the Prosumer's tab (pure display, no role-specific logic there).
 *
 * Unlike the Prosumer's Map/List toggle, the map fills the whole screen (including behind the
 * status bar/notch - this fragment alone pulls its root view up under it via a negative top
 * margin sized from the window insets, so Home/Bookings/Scan are unaffected) and the station
 * list lives in a persistent bottom sheet (own layout, fragment_operator_map.xml) that the
 * operator drags between a collapsed peek and half the screen - capped there via
 * BottomSheetBehavior.expandedOffset so it never covers more than that. The map's own padding
 * is kept in sync with the sheet's state so its logical center/zoom-to-fit stay in the visible
 * (unobstructed) top portion. Tapping a station - in the list or as a marker - zooms the map to
 * that station instead of opening a detail dialog, since the list row already shows the same
 * capacity/slots/status detail.
 */
class OperatorMapFragment : Fragment(R.layout.fragment_operator_map), OnMapReadyCallback {

    private val viewModel: MapViewModel by viewModels {
        viewModelFactory { initializer { MapViewModel(ServiceLocator.stationRepository) } }
    }

    private var googleMap: GoogleMap? = null
    private var stationById: Map<String, SolarStation> = emptyMap()
    private var markerByStationId: MutableMap<String, Marker> = mutableMapOf()
    private var pendingStations: List<SolarStation>? = null

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var containerHeight = 0
    private lateinit var swipeRefreshMap: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressMap: ProgressBar
    private lateinit var tvEmptyMap: TextView
    private lateinit var tvMapBanner: TextView
    private lateinit var adapter: StationListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Let the map bleed under the status bar/notch: push this fragment's own root up by
        // exactly the top inset, while its bottom edge stays anchored (see class doc). Scoped
        // to this fragment only - no other operator screen is affected.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val params = v.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null && params.topMargin != -statusBarInset) {
                params.topMargin = -statusBarInset
                v.layoutParams = params
            }
            insets
        }
        ViewCompat.requestApplyInsets(view)

        val bottomSheet = view.findViewById<View>(R.id.bottomSheetStations)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
        }
        // Cap "maximized" at half the screen instead of full-screen. A persistent listener
        // (not a one-shot doOnLayout) because the edge-to-edge inset margin above triggers a
        // second, taller layout pass shortly after the first.
        view.addOnLayoutChangeListener { v, _, _, _, bottom, _, _, _, oldBottom ->
            if (v.height != containerHeight) {
                containerHeight = v.height
                bottomSheetBehavior.expandedOffset = v.height / 2
            }
        }
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) {
                val bottomPadding = when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.STATE_HALF_EXPANDED ->
                        containerHeight - bottomSheetBehavior.expandedOffset
                    BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.peekHeight
                    else -> return
                }
                googleMap?.setPadding(0, 0, 0, bottomPadding)
            }

            override fun onSlide(sheet: View, slideOffset: Float) = Unit
        })

        swipeRefreshMap = view.findViewById(R.id.swipeRefreshMap)
        recyclerView = view.findViewById(R.id.rvStations)
        progressMap = view.findViewById(R.id.progressMap)
        tvEmptyMap = view.findViewById(R.id.tvEmptyMap)
        tvMapBanner = view.findViewById(R.id.tvMapBanner)

        adapter = StationListAdapter { station -> focusStation(station) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        swipeRefreshMap.setOnRefreshListener { viewModel.loadStations() }

        val playServicesAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(requireContext()) == com.google.android.gms.common.ConnectionResult.SUCCESS

        if (playServicesAvailable) {
            val mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer) as com.google.android.gms.maps.SupportMapFragment
            mapFragment.getMapAsync(this)
        } else {
            // No Play Services (common on some emulators/devices) - nothing to show behind the
            // sheet, so expand it fully to make the station list the primary view instead.
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
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
        val initialBottomPadding = if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            containerHeight - bottomSheetBehavior.expandedOffset
        } else {
            bottomSheetBehavior.peekHeight
        }
        map.setPadding(0, 0, 0, initialBottomPadding)
        pendingStations?.let { renderMarkers(it) }
    }

    private fun renderMarkers(stations: List<SolarStation>) {
        val map = googleMap ?: run { pendingStations = stations; return }
        map.clear()
        markerByStationId.clear()
        for (station in stations) {
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(station.latitude, station.longitude))
                    .title(station.stationName)
                    .icon(BitmapDescriptorFactory.defaultMarker(StationMarkerIcons.hueFor(station.status)))
            )
            if (marker != null) {
                marker.tag = station.stationId
                markerByStationId[station.stationId] = marker
            }
        }
        map.setOnMarkerClickListener { marker ->
            val stationId = marker.tag as? String
            val station = stationId?.let { stationById[it] }
            if (station != null) {
                focusStation(station)
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

    private fun isOnline(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Zooms the map to the station and collapses the sheet so the zoomed-in map is visible. */
    private fun focusStation(station: SolarStation) {
        val map = googleMap ?: return
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(station.latitude, station.longitude), 16f))
        markerByStationId[station.stationId]?.showInfoWindow()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
}
