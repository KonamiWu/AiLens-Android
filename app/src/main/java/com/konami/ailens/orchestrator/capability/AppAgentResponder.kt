package com.konami.ailens.orchestrator.capability


object AppAgentResponder {

    // --- GET Success ---
    fun volumeGet(level: Int) = AgentResponse.ok(
        tool = AppToAgentTool.Volume,
        operation = Operation.GET,
        data = VolumeData(level)
    )

    fun brightnessGet(level: Int) = AgentResponse.ok(
        tool = AppToAgentTool.Brightness,
        operation = Operation.GET,
        data = BrightnessData(level)
    )

    fun screenModeGet(mode: String) = AgentResponse.ok(
        tool = AppToAgentTool.ScreenMode,
        operation = Operation.GET,
        data = ScreenModeData(mode)
    )

    fun dndGet(enabled: Boolean) = AgentResponse.ok(
        tool = AppToAgentTool.Dnd,
        operation = Operation.GET,
        data = DNDData(enabled)
    )

    fun batteryGet(level: Int, isCharging: Boolean) = AgentResponse.ok(
        tool = AppToAgentTool.Battery,
        operation = Operation.GET,
        data = BatteryData(level, isCharging)
    )

    fun languageGet(language: String) = AgentResponse.ok(
        tool = AppToAgentTool.Language,
        operation = Operation.GET,
        data = LanguageData(language)
    )

    // --- SET Success ---
    fun setOK(tool: AppToAgentTool, message: String? = null) = if (message != null) {
        AgentResponse.ok(tool, Operation.SET, MessageOnly(message))
    } else {
        AgentResponse.ok(tool, Operation.SET, null, null)
    }

    // --- Done (for actions) ---
    fun done(tool: AppToAgentTool, note: String? = null): AgentResponse<MessageOnly> {
        val finalMessage = note ?: "tool ${tool.name} executed successfully on the client."
        return AgentResponse.ok(tool, data = MessageOnly(finalMessage))
    }

    // --- Errors ---
    fun missingOperation(tool: AppToAgentTool) =
        AgentResponse.error<MessageOnly>(tool, message = "Missing 'operation' for ${tool.name}")

    fun unsupportedOperation(tool: AppToAgentTool, op: String) =
        AgentResponse.error<MessageOnly>(tool, message = "Unsupported operation '$op' for ${tool.name}")

    fun missingParam(tool: AppToAgentTool, param: String) =
        AgentResponse.error<MessageOnly>(tool, message = "Missing required parameter '$param'")

    fun invalidValue(tool: AppToAgentTool, param: String, reason: String) =
        AgentResponse.error<MessageOnly>(tool, message = "Invalid value for '$param': $reason")

    fun failed(tool: AppToAgentTool, action: String) =
        AgentResponse.error<MessageOnly>(tool, message = "Failed to $action")
}
