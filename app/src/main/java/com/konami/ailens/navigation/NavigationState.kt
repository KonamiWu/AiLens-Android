package com.konami.ailens.navigation

import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

data class NavStep(
    val instruction: String,
    val distanceMeters: Int,
    val timeInSecond: Int,
    val maneuverIcon: Drawable?
)

class NavigationState {
    enum class NavigationError(val message: String) {
        NO_ROUTE_FOUND("No route found. Please check the start/end locations or travel mode."),
        NETWORK_ERROR("Network connection error. Please check your internet connection."),
        QUOTA_EXCEEDED("Quota exceeded."),
        UNKNOWN("Navigation failed")
    }

    val travelMode = MutableStateFlow(com.konami.ailens.orchestrator.Orchestrator.TravelMode.DRIVING)

    val currentStep = MutableStateFlow<NavStep?>(null)
    val remainingSteps = MutableStateFlow<List<NavStep>>(emptyList())
    val remainingDistanceMeters = MutableStateFlow<Double?>(null)
    val remainingTimeSec = MutableStateFlow<Long?>(null)
    val eta = MutableStateFlow<Instant?>(null)
    val error = MutableStateFlow<NavigationError?>(null)

    val isStarted = MutableSharedFlow<Boolean>(replay = 0)
    val isFinished = MutableSharedFlow<Boolean>(replay = 0)
}

