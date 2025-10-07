package com.konami.ailens.orchestrator

import android.util.Log
import com.konami.ailens.ble.BLEService
import com.konami.ailens.orchestrator.capability.AgentCapability
import com.konami.ailens.orchestrator.capability.AgentDisplayCapability
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.DeviceEventCapability
import com.konami.ailens.orchestrator.capability.Operation
import com.konami.ailens.orchestrator.capability.NavigationDisplayCapability
import com.konami.ailens.orchestrator.capability.ToolCapability
import com.konami.ailens.orchestrator.coordinator.AgentCoordinator
import com.konami.ailens.orchestrator.coordinator.NavigationCoordinator
import com.konami.ailens.orchestrator.role.Role
import com.konami.ailens.navigation.NavigationService
import com.konami.ailens.navigation.NavigationForegroundService
import com.konami.ailens.AiLensApplication
import io.socket.client.Ack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Orchestrator private constructor(): CapabilitySink, DeviceEventCapability, ToolCapability {
    enum class Language(val code: String, val title: String) {
        ENGLISH("en-US", "English"),
        ESPANOL("es-ES", "Español"),
        FRANCAIS("fr-FR", "Français"),
        CHINESE("zh-TW", "中文"),
        JAPANESE("ja-JP", "日本語");

        companion object {
            fun fromCode(code: String): Language? {
                return entries.find { it.code == code }
            }
        }
    }

    enum class TravelMode {
        WALKING,
        DRIVING,
        MOTORCYCLE
    }

    companion object {
        val instance: Orchestrator by lazy { Orchestrator() }
    }

    private var agent: AgentCapability? = null
    private val agentDisplays = mutableListOf<AgentDisplayCapability>()
    private var agentCoordinator: AgentCoordinator? = null
    private var navigationCoordinator: NavigationCoordinator? = null
    private val navigationDisplays = mutableListOf<NavigationDisplayCapability>()
    private var lastNavigationDestination: String? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {

        }
    }

    override fun setAgent(role: AgentCapability) {
        agent = role
    }

    override fun addAgentDisplay(role: AgentDisplayCapability) {
        agentDisplays.add(role)
    }

    fun register(role: Role) {
        role.registerCapabilities(this)
    }

    fun unregister(role: Role) {
        if (role === agent) {
            agent = null
        }
        agentDisplays.removeAll { it === role }
    }

    fun clean() {
        agent = null
        agentDisplays.clear()
    }

    override fun handleDeviceEvent(event: DeviceEventCapability.DeviceEvent) {
        when (event) {
            DeviceEventCapability.DeviceEvent.EnterAgent -> {
                val agent = agent ?: return

                agentCoordinator = AgentCoordinator(agent, agentDisplays, this)
                agentCoordinator?.start()
            }
            DeviceEventCapability.DeviceEvent.EnterDialogueTranslation -> {

            }
            is DeviceEventCapability.DeviceEvent.EnterNavigation -> {

            }
            is DeviceEventCapability.DeviceEvent.EnterNavigation2D -> {

            }
            DeviceEventCapability.DeviceEvent.EnterSimultaneousTranslation -> {

            }
            DeviceEventCapability.DeviceEvent.LeaveAgent -> {
                agentCoordinator?.stop()
                agentCoordinator = null
            }
            DeviceEventCapability.DeviceEvent.LeaveDialogueTranslation -> {

            }
            DeviceEventCapability.DeviceEvent.LeaveNavigation -> {

            }
            DeviceEventCapability.DeviceEvent.LeaveSimultaneousTranslation -> {

            }
            is DeviceEventCapability.DeviceEvent.SetDialogueLanguages -> {

            }
            is DeviceEventCapability.DeviceEvent.SetSimultaneousLanguages -> {

            }
        }
    }

    override fun handleVolume(level: String?, operation: Operation, ack: Ack) {
        TODO("Not yet implemented")
    }

    override fun handleBrightness(level: String?, operation: Operation, ack: Ack) {
        TODO("Not yet implemented")
    }

    override fun handleScreenMode(mode: String, operation: Operation, ack: Ack) {
        TODO("Not yet implemented")
    }

    override fun handleDND(enabled: Boolean?, operation: Operation, ack: Ack) {
        TODO("Not yet implemented")
    }

    override fun handleBatteryRequest(operation: Operation, ack: Ack) {
        TODO("Not yet implemented")
    }

    override fun handleLanguage(language: String?, operation: Operation, ack: Ack) {
        TODO("Not yet implemented")
    }

    override fun handleNavigation(destination: String, mode: String, ack: Ack?) {
        val travelMode = when(mode.lowercase()) {
            "walking" -> TravelMode.WALKING
            "motorcycle" -> TravelMode.MOTORCYCLE
            else -> TravelMode.DRIVING
        }

        // Ensure foreground service keeps navigation alive and prepare map
        NavigationForegroundService.start(AiLensApplication.instance)
        NavigationService.ensureMap(AiLensApplication.instance)

        // Build coordinator on demand using the singleton NavigationService
        navigationCoordinator = NavigationCoordinator(
            navigationCapabilities = listOf(NavigationService),
            navigationDisplays = navigationDisplays,
            agentCapability = agent
        )
        lastNavigationDestination = destination
        navigationCoordinator?.start(destination, travelMode)
    }

    override fun handleTakePicture() {
        TODO("Not yet implemented")
    }

    override fun handleTranslationPage(source: String, target: String, bilingual: Boolean) {
        TODO("Not yet implemented")
    }

    override fun handleSportsWidget(team1: String, score1: String, team2: String, score2: String) {
        TODO("Not yet implemented")
    }

    override fun handleNewsWidget(headlines: List<String>) {
        TODO("Not yet implemented")
    }

    override fun handleWeatherWidget(icon: String, temp: String, desc: String) {
        TODO("Not yet implemented")
    }

    override fun handleStockWidget(name: String, price: String, change: String) {
        TODO("Not yet implemented")
    }

    override fun handleHealthWidget() {
        TODO("Not yet implemented")
    }

    override fun handleVersionRequest() {
        TODO("Not yet implemented")
    }

    override fun handleTakeVideo(duration: String) {
        TODO("Not yet implemented")
    }

    override fun handleStreamPage() {
        TODO("Not yet implemented")
    }

    override fun handleTeleprompter(script: String, mode: String, fontSize: String) {
        TODO("Not yet implemented")
    }

    override fun handlePOIList(pois: List<String>) {
        TODO("Not yet implemented")
    }

    override fun handleCompassPage() {
        TODO("Not yet implemented")
    }

    override fun handleUnknownTool(tool: String, args: Map<String, Any?>) {
        TODO("Not yet implemented")
    }

    override fun replyError(message: String, ack: Ack) {
        TODO("Not yet implemented")
    }
    // Public helpers to register/unregister navigation displays from UI layers
    fun addNavigationDisplay(display: NavigationDisplayCapability) {
        navigationDisplays.add(display)
    }

    fun removeNavigationDisplay(display: NavigationDisplayCapability) {
        navigationDisplays.remove(display)
    }

    fun updateNavigationMode(mode: TravelMode) {
        val dest = lastNavigationDestination ?: return
        navigationCoordinator?.start(dest, mode)
    }
}
