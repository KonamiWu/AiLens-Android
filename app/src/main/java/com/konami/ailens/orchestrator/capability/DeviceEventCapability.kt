package com.konami.ailens.orchestrator.capability

import com.google.android.gms.maps.model.LatLng
import com.konami.ailens.orchestrator.Orchestrator

interface DeviceEventCapability {
    sealed class DeviceEvent {
        data object EnterDialogueTranslation : DeviceEvent()
        data object LeaveDialogueTranslation : DeviceEvent()
        data object EnterSimultaneousTranslation : DeviceEvent()
        data object LeaveSimultaneousTranslation : DeviceEvent()
        data object EnterAgent : DeviceEvent()
        data object LeaveAgent : DeviceEvent()
        data class EnterNavigation(
            val destination: String,
            val travelMode: Orchestrator.TravelMode
        ) : DeviceEvent()
        data class EnterNavigation2D(
            val destination: LatLng,
            val travelMode: Orchestrator.TravelMode
        ) : DeviceEvent()
        data object LeaveNavigation : DeviceEvent()
        data class SetDialogueLanguages(
            val source: Orchestrator.Language,
            val target: Orchestrator.Language
        ) : DeviceEvent()
        data class SetSimultaneousLanguages(
            val source: Orchestrator.Language,
            val target: Orchestrator.Language
        ) : DeviceEvent()
    }

    fun handleDeviceEvent(event: DeviceEvent)
}