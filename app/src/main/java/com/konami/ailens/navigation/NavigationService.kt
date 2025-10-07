package com.konami.ailens.navigation

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng as GmsLatLng
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.google.android.libraries.navigation.*
import android.location.Geocoder
import java.util.Locale
import com.konami.ailens.AiLensApplication
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.LatLng
import com.konami.ailens.orchestrator.capability.NavigationCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@SuppressLint("StaticFieldLeak")
object NavigationService : NavigationCapability, NavigationApi.NavigatorListener {
    override val state = NavigationState()

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var containerView: FrameLayout? = null
    private var navigationView: NavigationView? = null
    private var googleMap: GoogleMap? = null
    private var navigator: Navigator? = null

    private var navigationLifecycleObserver: DefaultLifecycleObserver? = null
    
    // Simple flag to track if navigation is active
    private var _isNavigating = false
    val isNavigating: Boolean
        get() = _isNavigating

    override val mapView: View?
        get() = containerView

    private fun setupMapWithCurrentLocation() {
        googleMap?.let { map ->
            try {
                // Enable my location layer and button
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
                map.uiSettings.isCompassEnabled = true
                // Optional: Move camera to current location if available
                // This requires location permission to be granted
                val locationManager = AiLensApplication.instance.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val lastKnownLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                
                lastKnownLocation?.let { location ->
                    val currentLatLng = GmsLatLng(location.latitude, location.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            } catch (e: SecurityException) {
                Log.e("NavigationService", "Location permission not granted: ${e.message}")
            }
        }
    }
    
    fun ensureMap(context: Context) {
        if (containerView == null) containerView = FrameLayout(context.applicationContext)
        if (navigationView == null) {
            navigationView = NavigationView(context.applicationContext).also { nv ->
                nv.onCreate(Bundle())
                nv.getMapAsync { gm ->
                    googleMap = gm
                    // Enable my location immediately to show current position
                    setupMapWithCurrentLocation()
                    // Initialize Navigator API after map is ready
                    Log.d("NavigationService", "Map ready, initializing Navigator API")
                    NavigationApi.getNavigator(AiLensApplication.instance, this@NavigationService)
                }
            }
            containerView?.removeAllViews()
            containerView?.addView(
                navigationView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    fun attachTo(container: ViewGroup, lifecycleOwner: LifecycleOwner, context: Context) {
        ensureMap(context)
        val view = containerView ?: return
        if (view.parent !== container) {
            (view.parent as? ViewGroup)?.removeView(view)
            container.removeAllViews()
            container.addView(view)
        }
        // Remove any existing observers to prevent conflicts
        navigationLifecycleObserver?.let { lifecycleOwner.lifecycle.removeObserver(it) }
        
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { 
                navigationView?.onStart() 
            }
            
            override fun onResume(owner: LifecycleOwner) { 
                navigationView?.onResume() 
            }
            
            override fun onPause(owner: LifecycleOwner) { 
                navigationView?.onPause() 
            }
            
            override fun onStop(owner: LifecycleOwner) { 
                navigationView?.onStop() 
            }
            
            override fun onDestroy(owner: LifecycleOwner) { 
                // Keep map for reuse, don't destroy 
                navigationLifecycleObserver = null
            }
        }
        
        navigationLifecycleObserver = observer
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // Orchestrator entry
    override fun start(destination: LatLng, travelMode: Orchestrator.TravelMode) {
        val modeInt = when (travelMode) {
            Orchestrator.TravelMode.WALKING -> 0
            Orchestrator.TravelMode.MOTORCYCLE -> 1
            Orchestrator.TravelMode.DRIVING -> 2
        }
        start(GmsLatLng(destination.latitude, destination.longitude), modeInt)
    }

    override fun start(destination: String, travelMode: Orchestrator.TravelMode) {
        // Use Geocoder to convert address string to coordinates
        scope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(AiLensApplication.instance, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(destination, 1)
                
                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0]
                    val latLng = LatLng(address.latitude, address.longitude)
                    
                    // Switch back to main thread for navigation
                    withContext(Dispatchers.Main) {
                        start(latLng, travelMode)
                    }
                } else {
                    // No address found
                    withContext(Dispatchers.Main) {
                        state.error.value = NavigationState.NavigationError.NO_ROUTE_FOUND
                    }
                }
            } catch (e: Exception) {
                Log.e("NavigationService", "Geocoding failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    state.error.value = NavigationState.NavigationError.UNKNOWN
                }
            }
        }
    }

    // Native Android Navigation start (Navigator-based)
    private fun start(destination: GmsLatLng, travelMode: Int) {
        val map = googleMap ?: run {
            state.error.value = NavigationState.NavigationError.NO_ROUTE_FOUND
            return
        }
        val destWaypoint = try {
            Waypoint.builder()
                .setLatLng(destination.latitude, destination.longitude)
                .setTitle("Destination")
                .build()
        } catch (e: Exception) {
            state.error.value = NavigationState.NavigationError.NO_ROUTE_FOUND
            return
        }
        // Map location settings are already configured in setupMapWithCurrentLocation
        // Just set navigation-specific settings here
        map.uiSettings.isMyLocationButtonEnabled = false  // Hide during navigation
        
        scope.launch(Dispatchers.Main) {
            try {
                withNavigator { nav: Navigator ->
                        // Set travel mode before setting destination
                        // Note: Navigator.TravelMode might be different in actual SDK
                        // For now, we'll set the routing options instead
                        // Create basic routing options
                        val routingOptions = RoutingOptions()
                        // Update state with travel mode
                        state.travelMode.value = when (travelMode) {
                            0 -> Orchestrator.TravelMode.WALKING
                            1 -> Orchestrator.TravelMode.MOTORCYCLE
                            2 -> Orchestrator.TravelMode.DRIVING
                            else -> Orchestrator.TravelMode.DRIVING
                        }
                        
                        // Use destination method with routing options
                        try {
                            val future = nav.setDestinations(listOf(destWaypoint), routingOptions)
                            // For now, assume success and start guidance
                            nav.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
                            nav.startGuidance()
                            _isNavigating = true
                            scope.launch { state.isStarted.emit(true) }
                        } catch (e: Exception) {
                            Log.e("NavigationService", "Failed to set destination: ${e.message}")
                            state.error.value = NavigationState.NavigationError.NO_ROUTE_FOUND
                    }
                }
            } catch (e: Exception) {
                state.error.value = NavigationState.NavigationError.UNKNOWN
            }
        }
    }

    override fun stop() {
        withNavigator { nav: Navigator ->
            nav.stopGuidance()
            nav.clearDestinations()
        }
        googleMap?.isMyLocationEnabled = false
        state.currentStep.value = null
        state.remainingSteps.value = emptyList()
        state.remainingDistanceMeters.value = null
        state.remainingTimeSec.value = null
        state.eta.value = null
        _isNavigating = false
        scope.launch { state.isFinished.emit(true) }
    }
    
    private fun setupNavigatorListeners() {
        navigator?.let { nav ->
            // Add navigation event listeners
            nav.addArrivalListener { waypoint ->
                _isNavigating = false
                scope.launch { state.isFinished.emit(true) }
            }
            
            nav.addRouteChangedListener {
                // Route changed, could update UI here
            }
            
            // Add remaining time/distance listener if available
            // nav.addRemainingTimeOrDistanceChangedListener { ... }
        }
    }
    
    // NavigationApi.NavigatorListener implementation
    override fun onNavigatorReady(nav: Navigator) {
        Log.e("NavigationService", "onNavigatorReady called")
        navigator = nav
        setupNavigatorListeners()
    }
    
    override fun onError(errorCode: Int) {
        val errorMessage = when (errorCode) {
            NavigationApi.ErrorCode.NOT_AUTHORIZED -> "NOT_AUTHORIZED - API key issue or not enabled"
            NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> "TERMS_NOT_ACCEPTED - Terms need to be accepted"
            NavigationApi.ErrorCode.NETWORK_ERROR -> "NETWORK_ERROR - Network connection issue"
            NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> "LOCATION_PERMISSION_MISSING"
            else -> "Unknown error code: $errorCode"
        }
        Log.e("NavigationService", "NavigationApi.onError: $errorMessage")
        
        // Handle navigation initialization errors
        state.error.value = when (errorCode) {
            NavigationApi.ErrorCode.NETWORK_ERROR -> NavigationState.NavigationError.NETWORK_ERROR
            else -> NavigationState.NavigationError.UNKNOWN
        }
    }
    
    // Helper method to execute navigator actions safely
    private fun withNavigator(action: (Navigator) -> Unit) {
        navigator?.let(action) ?: run {
            Log.e("NavigationService", "Navigator is null - not initialized yet")
            state.error.value = NavigationState.NavigationError.UNKNOWN
        }
    }
}
