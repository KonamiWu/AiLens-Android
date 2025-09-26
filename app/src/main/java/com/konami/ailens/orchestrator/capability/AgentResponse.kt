package com.konami.ailens.orchestrator.capability

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
data class AgentResponse<T>(
    val tool: AppToAgentTool,
    val status: ResponseStatus,
    val operation: Operation? = null,
    val data: T? = null,
    val message: String? = null
) {
    companion object {
        fun <T> ok(
            tool: AppToAgentTool,
            operation: Operation? = null,
            data: T? = null,
            message: String? = null
        ): AgentResponse<T> {
            return AgentResponse(tool, ResponseStatus.ok, operation, data, message)
        }

        fun <T> error(
            tool: AppToAgentTool,
            operation: Operation? = null,
            message: String
        ): AgentResponse<T> {
            return AgentResponse(tool, ResponseStatus.error, operation, null, message)
        }
    }
}

fun <T> AgentResponse<T>.toJsonObject(): JSONObject {
    return try {
        // Use Kotlinx Serialization to encode this response into a JSON string
        val jsonString = Json.encodeToString(this)
        JSONObject(jsonString)
    } catch (exception: Exception) {
        Log.e("AgentResponse", "toJsonObject failed: ${exception.message}")
        JSONObject()
    }
}