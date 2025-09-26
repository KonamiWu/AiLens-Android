package com.konami.ailens.orchestrator.role

import com.konami.ailens.orchestrator.capability.CapabilitySink

interface Role {
    fun registerCapabilities(sink: CapabilitySink)
}