package com.konami.ailens.api

import com.konami.ailens.api.RefreshTokenRequest.RefreshTokenResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FirmwareDownloadURLRequest(firmwareId: String): BaseRequest<FirmwareDownloadURLRequest.FirmwareDownloadURLResponse>(url = "${API.DOWNLOAD_FIRMWARE}/$firmwareId", method = Method.GET) {
    @Serializable
    data class FirmwareDownloadURLResponse(@SerialName("download_url") val downloadURL: String)

    override fun parseResponse(statusCode: Int, response: String): FirmwareDownloadURLResponse {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(response)
    }
}