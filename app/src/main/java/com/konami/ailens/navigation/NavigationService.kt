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
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavState
import com.google.android.libraries.mapsplatform.turnbyturn.model.StepInfo
import android.location.Geocoder
import android.util.DisplayMetrics
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
import androidx.lifecycle.Observer
import androidx.core.graphics.drawable.toDrawable
import com.konami.ailens.AppForegroundService

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

                // Disable default navigation UI (like iOS)
                nv.setEtaCardEnabled(false)  // Hide bottom ETA card
                nv.setHeaderEnabled(false)     // Keep top header for turn-by-turn instructions

                nv.getMapAsync { gm ->
                    googleMap = gm
                    setupMapWithCurrentLocation()
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

    fun setCustomFooter(view: View) {
        val navigationView = navigationView ?: return
        navigationView.setCustomControl(view, CustomControlPosition.FOOTER)
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
        map.uiSettings.isMyLocationButtonEnabled = false

        scope.launch(Dispatchers.Main) {
            try {
                withNavigator { nav: Navigator ->
                        val routingOptions = RoutingOptions()
                        // Update state with travel mode
                        state.travelMode.value = when (travelMode) {
                            0 -> Orchestrator.TravelMode.WALKING
                            1 -> Orchestrator.TravelMode.MOTORCYCLE
                            2 -> Orchestrator.TravelMode.DRIVING
                            else -> Orchestrator.TravelMode.DRIVING
                        }

                        try {
                            nav.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)

                            val future = nav.setDestinations(listOf(destWaypoint), routingOptions)
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
        Log.e("NavigationService", "stop() called")

        withNavigator { nav: Navigator ->
            nav.stopGuidance()
            nav.clearDestinations()
            // Disable audio guidance to prevent "GPS signal lost" warnings
            // when returning to map view without active navigation
            nav.setAudioGuidance(Navigator.AudioGuidance.SILENT)
        }
        // Stop foreground service
        AppForegroundService.stopFeature(AiLensApplication.instance, AppForegroundService.Feature.NAVIGATION)
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
                AppForegroundService.stopFeature(AiLensApplication.instance, AppForegroundService.Feature.NAVIGATION)
                scope.launch { state.isFinished.emit(true) }
            }

            nav.addRouteChangedListener {
                Log.e("NavigationService", "Route changed")
            }

            // Add remaining time/distance listener
            // This provides the TOTAL remaining time and distance for the entire route
            try {
                nav.addRemainingTimeOrDistanceChangedListener(
                    1,  // minTimeDeltaSeconds
                    1   // minDistanceDeltaMeters
                ) {
                    scope.launch {
                        try {
                            // Get total remaining time and distance for the route
                            val timeAndDistance = nav.currentTimeAndDistance
                            if (timeAndDistance != null) {
                                val seconds = timeAndDistance.seconds
                                val meters = timeAndDistance.meters

                                state.remainingTimeSec.value = seconds.toLong()
                                state.remainingDistanceMeters.value = meters.toDouble()

                                // Calculate ETA
                                if (seconds > 0) {
                                    val etaMillis = System.currentTimeMillis() + (seconds * 1000).toLong()
                                    state.eta.value = Instant.ofEpochMilli(etaMillis)
                                }

                                Log.e("NavigationService", "Route remaining time: ${seconds}s, distance: ${meters}m")
                            }
                        } catch (e: Exception) {
                            Log.e("NavigationService", "Error getting time/distance: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NavigationService", "Failed to add time/distance listener: ${e.message}")
            }

            // Register NavInfoReceivingService for turn-by-turn navigation updates
            registerNavInfoService(nav)

            // Observe NavInfo updates from the service
            NavInfoReceivingService.navInfoLiveData.observeForever(navInfoObserver)
        }
    }

    private fun registerNavInfoService(nav: Navigator) {
        try {
            val context = AiLensApplication.instance

            // Create custom DisplayMetrics for smaller maneuver icons (64x64 target)
            // density=1.0 produces 128x128, so use 0.5 for 64x64
            val customMetrics = DisplayMetrics().apply {
                density = 0.5f  // 0.5 = 64x64, 1.0 = 128x128, 1.5 = 192x192
                densityDpi = DisplayMetrics.DENSITY_LOW  // 120 dpi
                widthPixels = 480
                heightPixels = 800
            }

            val options = NavigationUpdatesOptions.builder()
                .setNumNextStepsToPreview(5) // Preview next 5 steps
                .setDisplayMetrics(customMetrics)  // Use custom metrics for smaller icons
                .setGeneratedStepImagesType(NavigationUpdatesOptions.GeneratedStepImagesType.BITMAP)
                .build()

            val isRegistered = nav.registerServiceForNavUpdates(
                context.packageName,
                NavInfoReceivingService::class.java.name,
                options
            )

            if (isRegistered) {
                Log.e("NavigationService", "Successfully registered NavInfoReceivingService")
            } else {
                Log.e("NavigationService", "Failed to register NavInfoReceivingService")
            }
        } catch (e: Exception) {
            Log.e("NavigationService", "Error registering NavInfo service: ${e.message}", e)
        }
    }

    private val navInfoObserver = Observer<NavInfo?> { navInfo ->
        if (navInfo != null) {
            handleNavInfoUpdate(navInfo)
        }
    }

    private fun handleNavInfoUpdate(navInfo: NavInfo) {
        try {

            navInfo.currentStep?.let { stepInfo ->

                val navStep = convertStepInfoToNavStep(
                    stepInfo,
                    distanceMeters = navInfo.distanceToCurrentStepMeters?.toInt(),
                    timeInSecond = navInfo.timeToCurrentStepSeconds?.toInt()
                )
                val previousStep = state.currentStep.value

                // Only log when step actually changes
                if (previousStep?.instruction != navStep.instruction) {
                    state.currentStep.value = navStep
                    Log.e("NavigationService", "Step changed: ${navStep.instruction}, distance: ${navStep.distanceMeters}m")
                } else {
                    state.currentStep.value = navStep
                }
            }

            // Update remaining steps
            navInfo.remainingSteps?.let { steps ->
                val navSteps = steps.map { convertStepInfoToNavStep(it) }
                val previousCount = state.remainingSteps.value.size

                // Only log when step count changes
                if (previousCount != navSteps.size) {
                    state.remainingSteps.value = navSteps
                    Log.e("NavigationService", "Remaining steps changed: ${navSteps.size}")
                } else {
                    state.remainingSteps.value = navSteps
                }
            }
        } catch (e: Exception) {
            Log.e("NavigationService", "Error handling NavInfo update: ${e.message}", e)
        }
    }

    private fun convertStepInfoToNavStep(
        stepInfo: StepInfo,
        distanceMeters: Int? = null,
        timeInSecond: Int? = null
    ): NavStep {
        // Get instruction text (prefer full instruction)
        val instruction = stepInfo.fullInstructionText
            ?: stepInfo.fullRoadName
            ?: stepInfo.simpleRoadName
            ?: ""

        // Get maneuver icon drawable
        val maneuverDrawable = try {
            stepInfo.maneuverBitmap?.let { bitmap ->
                bitmap.toDrawable(AiLensApplication.instance.resources)
            }
        } catch (e: Exception) {
            Log.e("NavigationService", "Failed to get maneuver icon: ${e.message}")
            null
        }

        return NavStep(
            instruction = instruction,
            distanceMeters = distanceMeters ?: stepInfo.distanceFromPrevStepMeters?.toInt() ?: 0,
            timeInSecond = timeInSecond ?: stepInfo.timeFromPrevStepSeconds?.toInt() ?: 0,
            maneuverIcon = maneuverDrawable
        )
    }


    // NavigationApi.NavigatorListener implementation
    override fun onNavigatorReady(nav: Navigator) {
        Log.e("NavigationService", "onNavigatorReady called")
        navigator = nav

        // Set audio guidance to enabled by default
        // This ensures voice prompts work from the start
        nav.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
        Log.e("NavigationService", "Audio guidance set to VOICE_ALERTS_AND_GUIDANCE")

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
