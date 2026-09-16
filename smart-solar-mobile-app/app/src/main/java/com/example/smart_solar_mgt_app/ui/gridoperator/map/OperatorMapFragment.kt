package com.example.smart_solar_mgt_app.ui.gridoperator.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
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
 * status bar/notch and the floating nav pill, courtesy of the edge-to-edge NavHost set up in
 * OperatorMainActivity) and the station list lives in a persistent bottom sheet (own layout,
 * fragment_operator_map.xml) that the operator drags between a collapsed peek and half the
 * screen - capped there via BottomSheetBehavior.expandedOffset so it never covers more than
 * that.
 *
 * The list's own container (R.id.stationListArea) gets its height set explicitly in code here,
 * recalculated continuously as the sheet drags (onSlide) rather than once: the sheet's internal
 * content lives in a coordinate space that is much taller than any single visible window into
 * it (that's how BottomSheetBehavior does the collapsed/expanded slide), so "how much of the
 * list is visible right now" changes with the sheet's position - a single fixed height cannot
 * correctly reserve the nav pill's footprint for both the collapsed peek and the expanded state
 * at once. (Also, layout_weight on this container does not reliably shrink the way it would in
 * a plain LinearLayout when BottomSheetBehavior is involved - confirmed empirically - so the
 * height cannot be delegated to XML at all here.) Without this, station rows can render - and
 * be hit-tested - underneath the pill, which draws on top and steals their taps. The map's own
 * padding is kept in sync with the sheet's state so its logical center/zoom-to-fit stay in the
 * visible (unobstructed) top portion. Tapping a station - in the list or as a marker - zooms the
 * map to that station instead of opening a detail dialog, since the list row already shows the
 * same capacity/slots/status detail.
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
    private lateinit var bottomSheet: View
    private var containerHeight = 0
    private lateinit var stationListArea: View
    private lateinit var swipeRefreshMap: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressMap: ProgressBar
    private lateinit var tvEmptyMap: TextView
    private lateinit var tvMapBanner: TextView
    private lateinit var adapter: StationListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The NavHost itself now bleeds edge-to-edge for every operator screen (see
        // OperatorMainActivity), so the map here already reaches the true top/bottom edges
        // with no extra work - unlike every other screen, this one deliberately doesn't call
        // View.applyEdgeToEdgeContentPadding, since the map is meant to show through.

        bottomSheet = view.findViewById(R.id.bottomSheetStations)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_COLLAPSED
            // Tall enough that the collapsed peek shows a full first card, not just a sliver -
            // the nav-pill clearance itself comes from updateListAreaHeight, not from this.
            peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height) +
                resources.getDimensionPixelSize(R.dimen.floating_nav_clearance)
        }
        stationListArea = view.findViewById(R.id.stationListArea)

        // Cap "maximized" at half the screen instead of full-screen. A persistent listener
        // (not a one-shot doOnLayout) because the edge-to-edge NavHost's own inset margins
        // trigger a second, taller layout pass shortly after the first.
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height != containerHeight) {
                containerHeight = v.height
                bottomSheetBehavior.expandedOffset = v.height / 2
            }
            updateListAreaHeight()
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
                updateListAreaHeight()
            }

            override fun onSlide(sheet: View, slideOffset: Float) = updateListAreaHeight()
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

    /**
     * Sizes stationListArea so it ends exactly floating_nav_clearance above the true screen
     * bottom, no matter where the sheet currently sits. bottomSheet.top is the sheet's current
     * on-screen position (varies continuously while dragging); the visible window into its
     * content is (containerHeight - bottomSheet.top), and stationListArea.top (fixed - it's
     * just the drag handle + title above it) tells us where the list starts within that window.
     */
    private fun updateListAreaHeight() {
        if (containerHeight == 0) return
        val visibleWindowHeight = containerHeight - bottomSheet.top
        val clearance = resources.getDimensionPixelSize(R.dimen.floating_nav_clearance)
        val newHeight = (visibleWindowHeight - stationListArea.top - clearance).coerceAtLeast(0)
        val lp = stationListArea.layoutParams
        if (lp.height != newHeight) {
            lp.height = newHeight
            stationListArea.layoutParams = lp
        }
    }

    /** Zooms the map to the station and collapses the sheet so the zoomed-in map is visible. */
    private fun focusStation(station: SolarStation) {
        val map = googleMap ?: return
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(station.latitude, station.longitude), 16f))
        markerByStationId[station.stationId]?.showInfoWindow()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
}
