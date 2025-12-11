package com.konami.ailens.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng as GmsLatLng
import com.google.android.libraries.navigation.*
import com.google.android.libraries.mapsplatform.turnbyturn.model.NavInfo
import com.google.android.libraries.mapsplatform.turnbyturn.model.StepInfo
import com.konami.ailens.SharedPrefs
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import androidx.lifecycle.Observer
import androidx.core.graphics.drawable.toDrawable
import com.google.android.gms.maps.model.MapStyleOptions
import com.konami.ailens.R

@SuppressLint("StaticFieldLeak")
class NavigationService(private val context: Context) : NavigationCapability, NavigationApi.NavigatorListener {
    companion object {
        private const val TAG = "NavigationService"

        @SuppressLint("StaticFieldLeak")
        @Volatile private var _instance: NavigationService? = null

        val instance: NavigationService
            get() = _instance ?: throw IllegalStateException("Call NavigationService.init(context) first")

        fun init(context: Context) {
            if (_instance == null) {
                synchronized(this) {
                    if (_instance == null) {
                        _instance = NavigationService(context.applicationContext)
                        Log.d(TAG, "BLEService initialized (no permissions required at this stage)")
                    }
                }
            }
        }
    }

    override val state = NavigationState()

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var navigationView: NavigationView? = null
    private var googleMap: GoogleMap? = null
    private var navigator: Navigator? = null


    // Simple flag to track if navigation is active
    private var _isNavigating = false

    val isNavigating: Boolean
        get() = _isNavigating

    override val mapView: View?
        get() = navigationView

    private var isInitializing = false

    fun initializeNavigator(activity: Activity) {
        Log.e("NavigationService", "initializeNavigator called")
        if (navigator != null) {
            Log.e("NavigationService", "Navigator already exists, skipping initialization")
            return
        }

        if (isInitializing) {
            Log.e("NavigationService", "Already initializing, skipping")
            return
        }

        isInitializing = true

        // 檢查用戶是否已經接受過 Terms
        val termsAccepted = SharedPrefs.navigationTermsAccepted
        val termsCheckOption = if (termsAccepted) {
            Log.e("NavigationService", "Terms already accepted, using SKIPPED option")
            TermsAndConditionsCheckOption.SKIPPED
        } else {
            Log.e("NavigationService", "Terms not accepted yet, using ENABLED option")
            TermsAndConditionsCheckOption.ENABLED
        }

        NavigationApi.getNavigator(activity, this@NavigationService, termsCheckOption)
    }

    private fun focusCurrentLocation() {
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
                state.error.value = NavigationState.NavigationError.PERMISSION_FAILED
                Log.e("NavigationService", "Location permission not granted: ${e.message}")
            }
        }
    }

    fun setCustomFooter(view: View) {
        val navigationView = navigationView ?: return
        navigationView.setCustomControl(view, CustomControlPosition.FOOTER)
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
                        val modeInt = when (travelMode) {
                            Orchestrator.TravelMode.WALKING -> 0
                            Orchestrator.TravelMode.MOTORCYCLE -> 1
                            Orchestrator.TravelMode.DRIVING -> 2
                        }
                        start(GmsLatLng(address.latitude, address.longitude), modeInt)
                    }
                } else {
                    state.error.value = NavigationState.NavigationError.NO_ROUTE_FOUND
                }
            } catch (e: Exception) {
                Log.e("NavigationService", "Geocoding failed: ${e.message}")
                state.error.value = NavigationState.NavigationError.UNKNOWN
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
        map.uiSettings.isMyLocationButtonEnabled = true
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

    @SuppressLint("MissingPermission")
    override fun stop() {
        scope.launch {
            navigator?.stopGuidance()
            navigator?.clearDestinations()
            navigator?.setAudioGuidance(Navigator.AudioGuidance.SILENT)
            googleMap?.isMyLocationEnabled = false
            state.currentStep.value = null
            state.remainingSteps.value = emptyList()
            state.remainingDistanceMeters.value = null
            state.remainingTimeSec.value = null
            state.eta.value = null
            _isNavigating = false

            scope.launch { state.isFinished.emit(true) }

            // Restore AppForegroundService notification after navigation ends
            restoreAppNotification()
        }
    }

    private fun setupNavigatorListeners() {
        navigator?.let { nav ->
            // Add navigation event listeners
            nav.addArrivalListener { waypoint ->
                _isNavigating = false
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
                            state.error.value = NavigationState.NavigationError.UNKNOWN
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
                state.error.value = NavigationState.NavigationError.UNKNOWN
            }
        } catch (e: Exception) {
            state.error.value = NavigationState.NavigationError.UNKNOWN
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
            state.error.value = NavigationState.NavigationError.UNKNOWN
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
            state.error.value = NavigationState.NavigationError.UNKNOWN
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
        Log.e("NavigationService", "Current navigator: $navigator, new nav: $nav")

        // 如果已經有 navigator 且是同一個實例，跳過
        if (navigator != null && navigator === nav) {
            Log.e("NavigationService", "Navigator already initialized (same instance), skipping")
            isInitializing = false
            return
        }

        // 如果是不同的 navigator 實例，清理舊的
        if (navigator != null && navigator !== nav) {
            Log.e("NavigationService", "New navigator instance detected, cleaning up old one")
            try {
                navigator?.stopGuidance()
                navigator?.clearDestinations()
            } catch (e: Exception) {
                Log.e("NavigationService", "Error cleaning up old navigator: ${e.message}")
            }
        }

        navigator = nav
        isInitializing = false

        // 用戶已經接受 Terms（否則不會到達這裡），保存狀態
        if (!SharedPrefs.navigationTermsAccepted) {
            Log.e("NavigationService", "Saving terms acceptance state")
            SharedPrefs.navigationTermsAccepted = true
        }

        // Set audio guidance to enabled by default
        // This ensures voice prompts work from the start
        nav.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)

        // 只在第一次初始化時創建 NavigationView
        if (navigationView == null) {
            navigationView = NavigationView(context.applicationContext).also { nv ->
                nv.onCreate(Bundle())
                nv.setEtaCardEnabled(false)  // Hide bottom ETA card
                nv.setHeaderEnabled(false)     // Keep top header for turn-by-turn instructions
                nv.getMapAsync { gm ->
                    googleMap = gm
                }
            }
        }

        // Don't recreate NavigationView - it's already created in initializeNavigator()
        // Just setup listeners
        setupNavigatorListeners()
    }

    override fun onError(errorCode: Int) {
        isInitializing = false

        val errorMessage = when (errorCode) {
            NavigationApi.ErrorCode.NOT_AUTHORIZED -> "NOT_AUTHORIZED - API key issue or not enabled"
            NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> "TERMS_NOT_ACCEPTED - Terms need to be accepted"
            NavigationApi.ErrorCode.NETWORK_ERROR -> "NETWORK_ERROR - Network connection issue"
            NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> "LOCATION_PERMISSION_MISSING"
            else -> "Unknown error code: $errorCode"
        }
        state.error.value = NavigationState.NavigationError.UNKNOWN
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

    // Restore AppForegroundService notification after navigation ends
    private fun restoreAppNotification() {
        try {
            val intent = android.content.Intent(context, com.konami.ailens.ble.AppForegroundService::class.java)
            context.startService(intent)
            Log.d(TAG, "AppForegroundService notification restored")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore AppForegroundService notification: ${e.message}", e)
        }
    }
}
