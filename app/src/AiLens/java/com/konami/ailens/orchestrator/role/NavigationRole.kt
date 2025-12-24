package com.konami.ailens.orchestrator.role

import android.view.View
import com.konami.ailens.navigation.NavigationService
import com.konami.ailens.navigation.NavigationState
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.NavigationCapability

class NavigationRole: Role, NavigationCapability {
    private val service = NavigationService.instance

    override fun registerCapabilities(sink: CapabilitySink) {
        sink.addNavigation(this)
    }

    override val state: NavigationState
        get() = service.state

    override val mapView: View?
        get() = service.mapView

    override fun start(destination: String, travelMode: Orchestrator.TravelMode) {
        service.start(destination, travelMode)
    }

    override fun stop() {
        service.stop()
    }
}