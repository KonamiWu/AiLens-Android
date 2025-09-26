package com.konami.ailens.orchestrator.capability
import io.socket.client.Ack

interface ToolCapability {
    fun handleVolume(level: String?, operation: Operation, ack: Ack)
    fun handleBrightness(level: String?, operation: Operation, ack: Ack)
    fun handleScreenMode(mode: String, operation: Operation, ack: Ack)
    fun handleDND(enabled: Boolean?, operation: Operation, ack: Ack)
    fun handleBatteryRequest(operation: Operation, ack: Ack)
    fun handleLanguage(language: String?, operation: Operation, ack: Ack)
    fun handleNavigation(destination: String, mode: String, ack: Ack?)

    fun handleTakePicture()
    fun handleTranslationPage(source: String, target: String, bilingual: Boolean)
    fun handleSportsWidget(team1: String, score1: String, team2: String, score2: String)
    fun handleNewsWidget(headlines: List<String>)
    fun handleWeatherWidget(icon: String, temp: String, desc: String)
    fun handleStockWidget(name: String, price: String, change: String)
    fun handleHealthWidget()
    fun handleVersionRequest()
    fun handleTakeVideo(duration: String)
    fun handleStreamPage()
    fun handleTeleprompter(script: String, mode: String, fontSize: String)
    fun handlePOIList(pois: List<String>)
    fun handleCompassPage()

    // fallback
    fun handleUnknownTool(tool: String, args: Map<String, Any?>)

    fun replyError(message: String, ack: Ack)
}
