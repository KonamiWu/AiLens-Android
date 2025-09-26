package com.konami.ailens.agent


import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import com.konami.ailens.StreamingPCMPlayer
import com.konami.ailens.orchestrator.Orchestrator
import com.konami.ailens.orchestrator.capability.AgentToAppTool
import com.konami.ailens.orchestrator.capability.AppAgentResponder
import com.konami.ailens.orchestrator.capability.AppToAgentTool
import com.konami.ailens.orchestrator.capability.Operation
import com.konami.ailens.orchestrator.capability.ToolCapability
import com.konami.ailens.orchestrator.capability.toJsonObject
import com.konami.ailens.recorder.Recorder
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI

class AgentService private constructor() {
    companion object {
        private const val TAG = "AgentService"

        val instance: AgentService by lazy { AgentService() }
    }

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _inputTranscript = MutableStateFlow("")
    val inputTranscript: StateFlow<String> = _inputTranscript

    private val _outputTranscript = MutableStateFlow("")
    val outputTranscript: StateFlow<String> = _outputTranscript

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _agentCompleted = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val agentCompleted: SharedFlow<Unit> = _agentCompleted

    // ======= Private =======
    private var socket: Socket? = null
    private var authToken: String? = null
    private var recordingJob: Job? = null
    private var tempResult: String = ""
    private var recorder: Recorder? = null
    private val player = StreamingPCMPlayer(24000, 1)

    private var lastAck: Ack? = null

    var toolCapability: ToolCapability? = null

    // ======= Public =======
    fun connect(token: String, env: EnvironmentConfig, serviceMode: String, language: String) {
        this.authToken = token

        if (socket != null && socket!!.connected()) {
            return
        }

        // === Query Params ===
        val queryParams = if (serviceMode == "agent") {
            "sessionType=${env.sessionType}"
        } else {
            "destinationLanguage=$language"
        }

        val opts = IO.Options().apply {
            forceNew = true
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 5000
            extraHeaders = mapOf("Authorization" to listOf("Bearer $token"))
            transports = arrayOf(io.socket.engineio.client.transports.WebSocket.NAME)
            query = queryParams
        }

        // === Namespace ===
        val namespace = if (serviceMode == "agent") env.websocketEndpoint else env.translationEndpoint
        val uri = URI.create(env.backendURL + namespace)

        socket = IO.socket(uri, opts).apply {
            on(Socket.EVENT_CONNECT, onConnect)
            on(Socket.EVENT_DISCONNECT, onDisconnect)
            on(Socket.EVENT_CONNECT_ERROR, onError)

            io().on(Manager.EVENT_RECONNECT_ATTEMPT, Emitter.Listener { args ->
                Log.e(TAG, "Manager: reconnect attempt ${args.joinToString()}")
            })
            io().on(Manager.EVENT_RECONNECT, Emitter.Listener { args ->
                Log.e(TAG, "Manager: reconnect succeeded ${args.joinToString()}")
            })

            setupCustomEventHandlers(this)
        }

        _connectionStatus.value = "Connecting..."
        socket?.connect()
    }

    fun setRecorder(recorder: Recorder) {
        this.recorder = recorder
    }

    fun disconnect() {
        stop()
        socket?.disconnect()
        socket = null
        _isConnected.value = false
        _connectionStatus.value = "Disconnected"
    }

    fun start() {
        if (_isConnected.value.not()) {
            Log.e("AgentService", "Cannot start recording: not connected")
            return
        }
        val recorder = recorder ?: return

        _isRecording.value = true
        recorder.startRecording()

        recordingJob = scope.launch {
            recorder.frames.collect { audioData ->
                sendAudioData(audioData)
            }
        }
    }


    fun stop() {
        if (_isRecording.value.not()) return
        recorder?.stopRecording()
        _outputTranscript.value = ""
        recordingJob?.cancel()
        recordingJob = null
        _isRecording.value = false
        emitEvent(SocketEvent.StopTranslation)
    }

    // ======= Handlers =======
    private val onConnect = Emitter.Listener {
        Log.e("AgentService", "Socket connected")
        _isConnected.value = true
        _connectionStatus.value = "Connected"
        _isReady.value = true
    }

    private val onDisconnect = Emitter.Listener { args ->
        _isReady.value = false
        val reason = args.firstOrNull()?.toString() ?: "Unknown"
        Log.e("AgentService", "Socket disconnected: $reason")
        _isConnected.value = false
        _connectionStatus.value = "Disconnected"
    }

    private val onError = Emitter.Listener { args ->
        _isReady.value = false
        val err = args.firstOrNull()?.toString() ?: "Unknown error"
        Log.e("AgentService", "Socket error: $err")
        _lastError.value = err
        _connectionStatus.value = "Error: $err"
    }

    private fun setupCustomEventHandlers(s: Socket) {
        s.on(SocketEvent.Message.value) { /* no-op */ }

        s.on(SocketEvent.GeminiResponse.value) { args ->
            val obj = args.firstOrNull() as? JSONObject
            val text = obj?.optString("text") ?: return@on
//            _outputTranscript.value = text
        }

        s.on(SocketEvent.GeminiAudio.value) { args ->
            val base64 = args.firstOrNull() as? String ?: return@on
            val raw = Base64.decode(base64, Base64.DEFAULT)
            player.write(raw)
        }

        s.on(SocketEvent.GeminiInputTranscript.value) { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            val text = obj.optString("text")
            _inputTranscript.value = text
        }

        s.on(SocketEvent.GeminiOutputTranscript.value) { args ->
            val obj = args.firstOrNull() as? JSONObject ?: return@on
            val text = obj.optString("text")

            tempResult += text
        }

        s.on(SocketEvent.GeminiTurnComplete.value) {
            Log.e("TAG", "tempResult = ${tempResult}")
            _outputTranscript.value = tempResult
            tempResult = ""
            scope.launch { _agentCompleted.emit(Unit) }
        }

        s.on(SocketEvent.DeviceToolCall.value) { args ->
            Log.e("AgentService", "deviceToolCall = ${args.joinToString()}")
            val obj = args.getOrNull(0) as? JSONObject ?: return@on
            val ack = args.getOrNull(1) as? Ack ?: return@on
            handleDeviceToolCall(obj, ack)
        }

        s.on(SocketEvent.TranslationConnected.value) {
            Log.e("AgentService", "translation_connected")
        }

        s.on(SocketEvent.TranslationStarted.value) {
            Log.e("AgentService", "translation_started")
        }

        s.on(SocketEvent.GeminiSessionOpen.value) {
            Log.e("AgentService", "gemini_session_opened")
            _isReady.value = true
        }
    }

    fun replyNavigationError(message: String) {
        val response = AppAgentResponder.failed(AppToAgentTool.NavigationPage, message)
        lastAck?.call(response)
        lastAck = null
    }

    // ======= Utility =======
    private fun emitEvent(event: SocketEvent, data: JSONObject? = null) {
        val s = socket ?: return
        if (!s.connected()) return
        if (data != null) {
            s.emit(event.value, data)
        } else {
            s.emit(event.value)
        }
        Log.e("AgentService", "emit ${event.value}")
    }

    private fun sendAudioData(data: ByteArray) {
        val s = socket ?: return
        if (!s.connected()) return
        val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
        s.emit(SocketEvent.AudioStream.value, base64)
    }


    private fun handleDeviceToolCall(jsonObject: JSONObject, ack: Ack) {
        val toolRaw = jsonObject.optString("tool")
        val argumentObject = jsonObject.optJSONObject("args") ?: JSONObject()
        val toolEnum = AgentToAppTool.fromRaw(toolRaw)
        Log.e("AgentService", "deviceToolCall tool=$toolRaw, args=$argumentObject")
        lastAck = ack

        when (toolEnum) {
            AgentToAppTool.VOLUME -> {
                val operationString = argumentObject.optString("operation", "")
                val operation = Operation.fromRaw(operationString)
                val level = argumentObject.optString("level", null)

                if (operation != null) {
                    toolCapability?.handleVolume(level, operation, ack)
                } else {
                    val res = AppAgentResponder.missingOperation(AppToAgentTool.Volume).toJsonObject()
                    ack.call(res)
                }
            }

            AgentToAppTool.BRIGHTNESS -> {
                val operationString = argumentObject.optString("operation", "")
                val operation = Operation.fromRaw(operationString)
                val level = argumentObject.optString("level", null)

                if (operation != null) {
                    toolCapability?.handleBrightness(level, operation, ack)
                } else {
                    val res = AppAgentResponder.missingOperation(AppToAgentTool.Brightness).toJsonObject()
                    ack.call(res)
                }
            }

            AgentToAppTool.SCREEN_MODE -> {
                val operationString = argumentObject.optString("operation", "")
                val operation = Operation.fromRaw(operationString)
                val modeValue = argumentObject.optString("mode", "")

                if (operation != null) {
                    toolCapability?.handleScreenMode(modeValue, operation, ack)
                } else {
                    val res = AppAgentResponder.missingOperation(AppToAgentTool.ScreenMode).toJsonObject()
                    ack.call(res)
                }
            }

            AgentToAppTool.DND -> {
                val operationString = argumentObject.optString("operation", "")
                val operation = Operation.fromRaw(operationString)
                val enabled: Boolean? = if (argumentObject.has("enabled")) {
                    argumentObject.optBoolean("enabled")
                } else {
                    null
                }

                if (operation != null) {
                    toolCapability?.handleDND(enabled, operation, ack)
                } else {
                    val res = AppAgentResponder.missingOperation(AppToAgentTool.Dnd).toJsonObject()
                    ack.call(res)
                }
            }

            AgentToAppTool.BATTERY -> {
                val operationString = argumentObject.optString("operation", "")
                val operation = Operation.fromRaw(operationString)

                if (operation != null) {
                    toolCapability?.handleBatteryRequest(operation, ack)
                } else {
                    val res = AppAgentResponder.missingOperation(AppToAgentTool.Battery).toJsonObject()
                    ack.call(res)
                }
            }

            AgentToAppTool.LANGUAGE -> {
                val operationString = argumentObject.optString("operation", "")
                val operation = Operation.fromRaw(operationString)
                val language = argumentObject.optString("language", null)

                if (operation != null) {
                    toolCapability?.handleLanguage(language, operation, ack)
                } else {
                    val res = AppAgentResponder.missingOperation(AppToAgentTool.Language).toJsonObject()
                    ack.call(res)
                }
            }

            // ===== One-time / widgets =====
            AgentToAppTool.TAKE_ALL_PHOTO -> {
                toolCapability?.handleTakePicture()
            }

            AgentToAppTool.TRANSLATION_PAGE -> {
                val source = argumentObject.optString("source_lang", "")
                val target = argumentObject.optString("target_lang", "")
                val bilingual: Boolean = if (argumentObject.has("bilingual")) {
                    val anyValue = argumentObject.get("bilingual")
                    if (anyValue is Boolean) {
                        anyValue
                    } else {
                        argumentObject.optString("bilingual", "false").toBoolean()
                    }
                } else {
                    false
                }
                toolCapability?.handleTranslationPage(source, target, bilingual)
            }

            AgentToAppTool.SPORTS_WIDGET -> {
                val team1 = argumentObject.optString("team1", "")
                val score1 = argumentObject.optString("score1", "")
                val team2 = argumentObject.optString("team2", "")
                val score2 = argumentObject.optString("score2", "")
                toolCapability?.handleSportsWidget(team1, score1, team2, score2)
            }

            AgentToAppTool.NEWS_WIDGET -> {
                val headlines = mutableListOf<String>()
                val h1 = argumentObject.optString("headline1", "")
                if (h1.isNotEmpty()) {
                    headlines.add(h1)
                }
                val h2 = argumentObject.optString("headline2", "")
                if (h2.isNotEmpty()) {
                    headlines.add(h2)
                }
                val h3 = argumentObject.optString("headline3", "")
                if (h3.isNotEmpty()) {
                    headlines.add(h3)
                }
                toolCapability?.handleNewsWidget(headlines)
            }

            AgentToAppTool.WEATHER_WIDGET -> {
                val icon = argumentObject.optString("icon", "")
                val temp = argumentObject.optString("temp", "")
                val desc = argumentObject.optString("status", "")
                toolCapability?.handleWeatherWidget(icon, temp, desc)
            }

            AgentToAppTool.STOCK_TICKER -> {
                val name = argumentObject.optString("fullname", "")
                    .ifEmpty { argumentObject.optString("stock_name", "") }
                    .ifEmpty { argumentObject.optString("ticker", "") }

                val priceString: String = if (argumentObject.has("price_line")) {
                    val array = argumentObject.optJSONArray("price_line")
                    if (array != null) {
                        val builder = StringBuilder()
                        var index = 0
                        while (index < array.length()) {
                            if (index > 0) {
                                builder.append(",")
                            }
                            builder.append(array.optString(index))
                            index += 1
                        }
                        builder.toString()
                    } else {
                        ""
                    }
                } else {
                    argumentObject.optString("price", "")
                }

                val change = argumentObject.optString("change_pct", "")
                    .ifEmpty { argumentObject.optString("change", "") }

                toolCapability?.handleStockWidget(name, priceString, change)
            }

            AgentToAppTool.HEALTH_WIDGET -> {
                toolCapability?.handleHealthWidget()
            }

            AgentToAppTool.VERSION -> {
                toolCapability?.handleVersionRequest()
            }

            AgentToAppTool.TAKE_VIDEO -> {
                val duration = argumentObject.optString("duration", "")
                toolCapability?.handleTakeVideo(duration)
            }

            AgentToAppTool.STREAM_PAGE -> {
                toolCapability?.handleStreamPage()
            }

            AgentToAppTool.TELEPROMPTER -> {
                val script = argumentObject.optString("script_name", "")
                val mode = argumentObject.optString("mode", "")
                val fontSize = argumentObject.optString("font_size", "")
                toolCapability?.handleTeleprompter(script, mode, fontSize)
            }

            AgentToAppTool.POI_WIDGET -> {
                val pois = mutableListOf<String>()
                var index = 1
                while (index <= 5) {
                    val key = "poi$index"
                    val value = argumentObject.optString(key, "")
                    if (value.isNotEmpty()) {
                        pois.add(value)
                    }
                    index += 1
                }
                toolCapability?.handlePOIList(pois)
            }

            AgentToAppTool.NAVIGATION_PAGE -> {
                val destination = argumentObject.optString("destination", "")
                val mode = argumentObject.optString("mode", "walking")
                toolCapability?.handleNavigation(destination, mode, ack)
            }

            AgentToAppTool.COMPASS_PAGE -> {
                toolCapability?.handleCompassPage()
            }

            AgentToAppTool.UNKNOWN -> {
            }
        }
    }
}