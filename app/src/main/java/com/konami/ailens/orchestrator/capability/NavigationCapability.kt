package com.konami.ailens.orchestrator.capability

import android.view.View
import com.konami.ailens.navigation.NavigationState

/**
 * Lightweight coordinate holder to avoid extra map SDK types in the interface layer.
 */
data class LatLng(val latitude: Double, val longitude: Double)

interface NavigationCapability {
    val state: NavigationState
    val mapView: View?

    fun start(destination: LatLng, travelMode: com.konami.ailens.orchestrator.Orchestrator.TravelMode)
    fun start(destination: String, travelMode: com.konami.ailens.orchestrator.Orchestrator.TravelMode)
    fun stop()
}

