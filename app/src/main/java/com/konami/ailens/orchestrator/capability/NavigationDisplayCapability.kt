package com.konami.ailens.orchestrator.capability

import android.view.View
import com.konami.ailens.navigation.NavStep
import java.time.Instant

interface NavigationDisplayCapability {
    fun displayStartNavigation()
    fun displayEndNavigation()
    fun displayNavigation(step: NavStep, remainingTimeInSecond: Int)
    fun displayMap(mapView: View)
    fun displayRemainingDistance(meters: Int)
    fun displayRemainingTime(seconds: Int)
    fun displayETA(eta: Instant)
}

