package com.konami.ailens.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.konami.ailens.R
import com.konami.ailens.orchestrator.Orchestrator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import kotlinx.coroutines.launch

class NavigationMapFragment : Fragment() {

    interface Callbacks {
        fun getSelectedMode(): Orchestrator.TravelMode
        fun onDestinationSelected(address: String)
        fun onRouteSummary(durationText: String, distanceText: String)
        fun getContainerHeight(): Int
        fun getNavContainerHeight(): Int
    }

    private val callbacks: Callbacks by lazy { (parentFragment as Callbacks) }
    private lateinit var viewModel: NavigationMapViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_map_host, container, false)
        
        // Initialize ViewModel using parent fragment's scope to share with AddressPickerFragment
        viewModel = ViewModelProvider(requireParentFragment()).get(NavigationMapViewModel::class.java)
        
        val tag = "internal_map"
        val existing = childFragmentManager.findFragmentByTag(tag)
        val mapFragment = (existing as? SupportMapFragment)
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction().replace(R.id.mapHost, it, tag).commitNow()
            }

        mapFragment.getMapAsync { googleMap ->
            val ctx = requireContext()
            val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                try { googleMap.isMyLocationEnabled = true } catch (_: SecurityException) {}
                val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = lm.getProviders(true)
                var best: Location? = null
                for (p in providers) {
                    try {
                        val l = lm.getLastKnownLocation(p) ?: continue
                        if (best == null || l.time > best!!.time) best = l
                    } catch (_: SecurityException) {}
                }
                best?.let { loc ->
                    val target = LatLng(loc.latitude, loc.longitude)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16f))
                }
            }

            googleMap.setOnMapClickListener { tapped ->
                viewModel.handleMapTap(tapped, callbacks.getSelectedMode())
            }

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        viewModel.address.collect { text ->
                            if (text.isEmpty())
                                return@collect
                            callbacks.onDestinationSelected(text)
                        } 
                    }
                    launch {
                        viewModel.route.collect { route ->
                            if (route != null) {
                                callbacks.onRouteSummary(route.durationText, route.distanceText)
                                googleMap.clear()
                                val opts = PolylineOptions().addAll(route.points).color(0xFF4285F4.toInt()).width(10f)
                                googleMap.addPolyline(opts)
                                googleMap.addMarker(MarkerOptions().position(route.origin).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
                                googleMap.addMarker(MarkerOptions().position(route.destination).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
                                
                                // Auto-fit camera to show entire route like iOS version
                                // Always fit camera when route changes, regardless of current map position
                                view?.post {
                                    fitCameraToRoute(googleMap, route.points)
                                }
                            } else {
                                // Route cleared - just clear the map without changing camera
                                googleMap.clear()
                                callbacks.onRouteSummary("", "")
                            }
                        }
                    }
                }
            }
        }
        return root
    }
    
    private fun fitCameraToRoute(googleMap: GoogleMap, routePoints: List<LatLng>) {
        if (routePoints.isEmpty()) return
        
        // Create bounds from all route points
        val boundsBuilder = LatLngBounds.Builder()
        for (point in routePoints) {
            boundsBuilder.include(point)
        }
        val bounds = boundsBuilder.build()
        
        // Get precise measurements
        val resources = requireContext().resources
        val density = resources.displayMetrics.density
        val displayMetrics = resources.displayMetrics
        
        // Get accurate container height based on current state
        val containerHeight = callbacks.getNavContainerHeight()
        
        // Get system UI insets
        val rootView = requireActivity().findViewById<View>(android.R.id.content)
        val insets = ViewCompat.getRootWindowInsets(rootView)
        val systemBarsInsets = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        val topSystemInset = systemBarsInsets?.top ?: 0
        val bottomSystemInset = systemBarsInsets?.bottom ?: 0
        
        // Calculate container's final top position (after animation completes)
        val containerTop = displayMetrics.heightPixels - bottomSystemInset - containerHeight
        
        // Calculate precise padding for 4 directions
        val topPadding = topSystemInset + (16 * density).toInt() // Reduced from 40 to minimize top white space
        val leftPadding = (16 * density).toInt()
        val rightPadding = (16 * density).toInt()
        val bottomPadding = (displayMetrics.heightPixels - containerTop) + (60 * density).toInt() // Increased from 40 to avoid container overlap
        
        Log.d("NavigationMapFragment", "4-direction padding - top: $topPadding, left: $leftPadding, right: $rightPadding, bottom: $bottomPadding")
        
        // Calculate the visible map area considering asymmetric padding
        val visibleWidth = displayMetrics.widthPixels - leftPadding - rightPadding
        val visibleHeight = displayMetrics.heightPixels - topPadding - bottomPadding
        
        // Calculate the center of the visible area (offset from screen center due to asymmetric padding)
        val visibleCenterX = leftPadding + visibleWidth / 2.0
        val visibleCenterY = topPadding + visibleHeight / 2.0
        
        try {
            // Use the route center point
            val routeCenter = LatLng(
                (bounds.northeast.latitude + bounds.southwest.latitude) / 2,
                (bounds.northeast.longitude + bounds.southwest.longitude) / 2
            )
            
            // First move to route center without changing zoom
            val centerUpdate = CameraUpdateFactory.newLatLng(routeCenter)
            googleMap.moveCamera(centerUpdate)
            
            // Wait for projection to update, then calculate bounds that fit in visible area
            view?.post {
                try {
                    val projection = googleMap.projection
                    
                    // Convert visible bounds to screen coordinates
                    val visibleBounds = LatLngBounds.Builder()
                    
                    // Top-left corner of visible area
                    val topLeft = android.graphics.Point(leftPadding, topPadding)
                    val topLeftLatLng = projection.fromScreenLocation(topLeft)
                    
                    // Bottom-right corner of visible area  
                    val bottomRight = android.graphics.Point(
                        displayMetrics.widthPixels - rightPadding,
                        displayMetrics.heightPixels - bottomPadding
                    )
                    val bottomRightLatLng = projection.fromScreenLocation(bottomRight)
                    
                    // Create bounds for the visible area
                    visibleBounds.include(topLeftLatLng)
                    visibleBounds.include(bottomRightLatLng)
                    val visibleAreaBounds = visibleBounds.build()
                    
                    // Check if route bounds fit within visible bounds
                    val routeSpan = kotlin.math.max(
                        bounds.northeast.latitude - bounds.southwest.latitude,
                        bounds.northeast.longitude - bounds.southwest.longitude
                    )
                    val visibleSpan = kotlin.math.max(
                        visibleAreaBounds.northeast.latitude - visibleAreaBounds.southwest.latitude,
                        visibleAreaBounds.northeast.longitude - visibleAreaBounds.southwest.longitude
                    )
                    
                    // Calculate zoom adjustment
                    val currentZoom = googleMap.cameraPosition.zoom
                    val zoomAdjustment = kotlin.math.log(visibleSpan / routeSpan, 2.0).toFloat() - 0.5f // Add some margin
                    val targetZoom = kotlin.math.max(1f, kotlin.math.min(20f, currentZoom + zoomAdjustment))
                    
                    Log.d("NavigationMapFragment", "Route span: $routeSpan, Visible span: $visibleSpan")
                    Log.d("NavigationMapFragment", "Zoom adjustment: $zoomAdjustment, Target zoom: $targetZoom")
                    
                    val finalUpdate = CameraUpdateFactory.newLatLngZoom(routeCenter, targetZoom)
                    googleMap.animateCamera(finalUpdate, 1000, null)
                    
                } catch (e: Exception) {
                    Log.e("NavigationMapFragment", "Failed to calculate visible bounds", e)
                    // Fallback to simple center
                    val simpleUpdate = CameraUpdateFactory.newLatLngZoom(routeCenter, 14f)
                    googleMap.animateCamera(simpleUpdate, 1000, null)
                }
            }
            
        } catch (e: Exception) {
            Log.e("NavigationMapFragment", "Failed to fit camera to route with projection", e)
            // Fallback to simple bounds
            try {
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 100)
                googleMap.animateCamera(cameraUpdate, 1000, null)
            } catch (e2: Exception) {
                Log.e("NavigationMapFragment", "Failed to fit camera to route (fallback)", e2)
            }
        }
    }
}
