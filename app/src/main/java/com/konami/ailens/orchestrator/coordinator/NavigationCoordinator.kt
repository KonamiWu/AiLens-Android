package com.konami.ailens.orchestrator.coordinator

import android.util.Log
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.AgentCapability
import com.konami.ailens.orchestrator.capability.LatLng
import com.konami.ailens.orchestrator.capability.NavigationCapability
import com.konami.ailens.orchestrator.capability.NavigationDisplayCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NavigationCoordinator(
    private val navigationCapabilities: List<NavigationCapability>,
    private val navigationDisplays: MutableList<NavigationDisplayCapability>,
    private val agentCapability: AgentCapability?
)
{
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val jobs = mutableListOf<Job>()

    init { bind() }

    private fun bind() {
        jobs.forEach { it.cancel() }
        jobs.clear()

        navigationCapabilities.forEach { capability ->
            capability.mapView?.let { view ->
                navigationDisplays.forEach { it.displayMap(view) }
            }

            jobs += scope.launch {
                capability.state.currentStep.collectLatest { step ->
                    val time = capability.state.remainingTimeSec.value
                    if (step != null && time != null) {
                        navigationDisplays.forEach { it.displayNavigation(step, time.toInt()) }
                    }
                }
            }

            jobs += scope.launch {
                capability.state.remainingDistanceMeters.collectLatest { value ->
                    value?.let { v -> navigationDisplays.forEach { it.displayRemainingDistance(v.toInt()) } }
                }
            }

            jobs += scope.launch {
                capability.state.remainingTimeSec.collectLatest { value ->
                    value?.let { v -> navigationDisplays.forEach { it.displayRemainingTime(v.toInt()) } }
                }
            }

            jobs += scope.launch {
                capability.state.eta.collectLatest { value ->
                    value?.let { v -> navigationDisplays.forEach { it.displayETA(v) } }
                }
            }

            jobs += scope.launch {
                capability.state.error.collectLatest { value ->
                    value?.let { agentCapability?.replyNavigationError(it.message) }
                }
            }

            jobs += scope.launch {
                capability.state.isStarted.collectLatest { started ->
                    if (started) navigationDisplays.forEach { it.displayStartNavigation() }
                }
            }

            jobs += scope.launch {
                capability.state.isFinished.collectLatest { finished ->
                    if (finished) stop()
                }
            }
        }
    }

    fun start(destination: String, travelMode: Orchestrator.TravelMode) {
        navigationCapabilities.forEach { it.start(destination, travelMode) }
    }

    fun start(destination: LatLng, travelMode: Orchestrator.TravelMode) {
        navigationCapabilities.forEach { it.start(destination, travelMode) }
    }

    fun add(display: NavigationDisplayCapability) {
        navigationDisplays.add(display)
        bind()
    }

    fun stop() {
        navigationDisplays.forEach { it.displayEndNavigation() }
        navigationCapabilities.forEach { it.stop() }
    }
}

