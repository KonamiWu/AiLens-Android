package com.konami.ailens.api

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class GetFirmwareVersionRequest(
    version: String,
    device: String,
) : BaseRequest<GetFirmwareVersionRequest.FirmwareVersionResponse>(
    url = API.FIRMWARE_LAST+"/$device",
    method = Method.GET
) {

    @Serializable
    data class FirmwareVersionResponse(
        val hasUpdate: Boolean,
        val message: String? = null,
        val firmware: FirmwareData? = null
    ) {
        @Serializable
        data class FirmwareData(
            val id: Int,
            val created_at: String,
            val device_type: Int,
            val releasenotes: String,
            val sha256: String,
            val version: String
        )
    }

    override val queryParams = mapOf(
        "currentVersion" to version,
        "deviceType" to device
    )

    override fun parseResponse(statusCode: Int, response: String): FirmwareVersionResponse {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(response)
    }

    override fun debug(statusCode: Int, response: String) {
        Log.e("GetFirmwareRequest", "response = $response")
    }
}
