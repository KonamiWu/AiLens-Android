package com.konami.ailens.orchestrator.role

import android.util.Log
import com.konami.ailens.ble.DeviceSession
import com.konami.ailens.ble.command.TextToAgentCommand
import com.konami.ailens.ble.command.ToggleAgentCommand
import com.konami.ailens.orchestrator.capability.AgentDisplayCapability
import com.konami.ailens.orchestrator.capability.CapabilitySink
import com.konami.ailens.orchestrator.capability.DeviceEventCapability

class BluetoothRole(private val session: DeviceSession, private val deviceEventHandler: DeviceEventCapability): Role, AgentDisplayCapability {
    private var sessionId: UByte = 1u

    init {
        session.deviceEventHandler = deviceEventHandler
    }
    override fun registerCapabilities(sink: CapabilitySink) {
        sink.addAgentDisplay(this)
    }

    override fun displayResultAnswer(result: String) {
        val command = TextToAgentCommand(sessionId++, result)
        session.add(command)
    }

    override fun displayStartAgent() {
        sessionId = 0u
        val command = ToggleAgentCommand(true)
        session.add(command)
    }

    override fun displayStopAgent() {
        val command = ToggleAgentCommand(false)
        session.add(command)
    }
}