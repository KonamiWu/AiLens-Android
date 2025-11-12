package com.konami.ailens.orchestrator

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.konami.ailens.SharedPrefs
import com.konami.ailens.ble.BLEService
import com.konami.ailens.orchestrator.capability.AgentCapability
import com.konami.ailens.orchestrator.capability.AgentDisplayCapability
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.DeviceEventCapability
import com.konami.ailens.orchestrator.capability.InterpretationCapability
import com.konami.ailens.orchestrator.capability.Operation
import com.konami.ailens.orchestrator.capability.NavigationDisplayCapability
import com.konami.ailens.orchestrator.capability.ToolCapability
import com.konami.ailens.orchestrator.coordinator.AgentCoordinator
import com.konami.ailens.orchestrator.coordinator.NavigationCoordinator
import com.konami.ailens.orchestrator.role.Role
import com.konami.ailens.orchestrator.capability.NavigationCapability
import com.konami.ailens.orchestrator.coordinator.InterpretationCoordinator
import io.socket.client.Ack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Orchestrator private constructor(private val context: Context): CapabilitySink, DeviceEventCapability, ToolCapability {
    enum class Language(val code: String, val title: String) {
        ENGLISH("en-US", "English"),
        ESPANOL("es-ES", "Español"),
        FRANCAIS("fr-FR", "Français"),
        CHINESE("zh-TW", "中文"),
        JAPANESE("ja-JP", "日本語");
        companion object {
            val interpretationSourceDefault: Language = CHINESE
            val interpretationTargetDefault: Language = ENGLISH
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
        private const val TAG = "Orchestrator"
        @SuppressLint("StaticFieldLeak")
        @Volatile private var _instance: Orchestrator? = null
        val instance: Orchestrator
            get() = _instance ?: throw IllegalStateException("Call Orchestrator.init(context) first")

        fun init(context: Context) {
            if (_instance == null) {
                synchronized(this) {
                    if (_instance == null) {
                        _instance = Orchestrator(context.applicationContext)
                        Log.d(TAG, "Orchestrator initialized")
                    }
                }
            }
        }
    }


    private var agent: AgentCapability? = null
    private val agentDisplays = mutableListOf<AgentDisplayCapability>()
    private var agentCoordinator: AgentCoordinator? = null
    private var navigationCoordinator: NavigationCoordinator? = null
    private var interpretationCoordinator: InterpretationCoordinator? = null
    private val navigations = mutableListOf<NavigationCapability>()
    private val navigationDisplays = mutableListOf<NavigationDisplayCapability>()
    private val interpretations = mutableListOf<InterpretationCapability>()

    var interpretationSourceLanguage: Orchestrator.Language = SharedPrefs.getInterpretationSourceLanguage(context)
        private set
    var interpretationTargetLanguage: Orchestrator.Language = SharedPrefs.getInterpretationTargetLanguage(context)
        private set

    init {

    }

    override fun setAgent(role: AgentCapability) {
        agent = role
    }

    override fun addAgentDisplay(role: AgentDisplayCapability) {
        agentDisplays.add(role)
    }

    override fun addNavigationDisplay(role: NavigationDisplayCapability) {
        navigationDisplays.add(role)
        navigationCoordinator?.refresh()
    }

    override fun addNavigation(role: NavigationCapability) {
        navigations.add(role)
    }

    override fun addInterpretation(role: InterpretationCapability) {
        interpretations.add(role)
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

    fun stopNavigation() {
        navigationCoordinator?.stop()
        navigationCoordinator = null
    }

    fun clean() {
        agentCoordinator?.stop()
        agent?.stopAgent()

        agentDisplays.clear()
        navigationDisplays.clear()
        interpretations.clear()
        navigationCoordinator?.stop()
        navigationCoordinator = null
        agentCoordinator = null
        agent = null
    }

    fun startAgent() {
        val agent = agent ?: return
        agentCoordinator = AgentCoordinator(agent, agentDisplays, this)
        agentCoordinator?.start()
    }

    fun stopAgent() {
        agentCoordinator?.stop()
        agentCoordinator = null
    }

    fun startInterpretation() {

    }

    fun stopInterpretation() {

    }

    override fun handleDeviceEvent(event: DeviceEventCapability.DeviceEvent) {
        when (event) {
            DeviceEventCapability.DeviceEvent.EnterAgent -> {
                startAgent()
            }
            DeviceEventCapability.DeviceEvent.EnterDialogueTranslation -> {

            }
            is DeviceEventCapability.DeviceEvent.EnterNavigation -> {

            }
            is DeviceEventCapability.DeviceEvent.EnterNavigation2D -> {

            }
            DeviceEventCapability.DeviceEvent.EnterSimultaneousTranslation -> {
                startInterpretation()
            }
            DeviceEventCapability.DeviceEvent.LeaveAgent -> {
                stopAgent()
            }
            DeviceEventCapability.DeviceEvent.LeaveDialogueTranslation -> {

            }
            DeviceEventCapability.DeviceEvent.LeaveNavigation -> {
                stopNavigation()
            }
            DeviceEventCapability.DeviceEvent.LeaveSimultaneousTranslation -> {
                stopInterpretation()
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

        navigationCoordinator = NavigationCoordinator(
            navigationCapabilities = navigations,
            navigationDisplays = navigationDisplays,
            agentCapability = agent
        )
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

    // Public helper to unregister navigation displays from UI layers
    fun removeNavigationDisplay(display: NavigationDisplayCapability) {
        navigationDisplays.remove(display)
    }
}
