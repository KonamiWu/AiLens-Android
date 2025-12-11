package com.konami.ailens.ble

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.konami.ailens.AiLensApplication
import com.konami.ailens.R
import com.konami.ailens.api.API
import com.konami.ailens.api.GetFirmwareVersionRequest
import com.konami.ailens.api.SessionManager
import com.konami.ailens.utils.FirmwareDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Centralized firmware management
 * Handles device info, version check, download, and update state
 */
object FirmwareManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val context: Context
        get() = AiLensApplication.instance.applicationContext

    sealed class UpdateState {
        object Idle : UpdateState()
        object CheckingVersion : UpdateState()
        object UpdateAvailable : UpdateState()
        object Downloading : UpdateState()
        object DownloadCompleted : UpdateState()
        data class CheckFailed(val error: String) : UpdateState()
        data class DownloadFailed(val error: String) : UpdateState()
        object NoUpdate : UpdateState()
    }

    data class FirmwareInfo(
        val currentVersion: String = "",
        val newestVersion: String = "",
        val updateDetails: String = "",
        val fileSize: String = "",
        val downloadURL: String = "",
        val fileSavePath: String = ""
    )

    // State flows
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _firmwareInfo = MutableStateFlow(FirmwareInfo())
    val firmwareInfo: StateFlow<FirmwareInfo> = _firmwareInfo.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()


    private var localFirmwarePath: File? = null

    /**
     * Check for firmware update
     * Reads device info from glasses via BLE first
     */
    fun checkForUpdate(glasses: Glasses?) {
        if (glasses == null) {
            _state.value = UpdateState.DownloadFailed("Glasses not connected")
            return
        }

        _state.value = UpdateState.CheckingVersion

        // Read device info from glasses via BLE
        DeviceInfoReader.readDeviceInfo(glasses) { result ->
            result.onSuccess { deviceInfo ->
                Log.e("FirmwareManager", "Device info: $deviceInfo")

                // Update current version
                _firmwareInfo.value = _firmwareInfo.value.copy(
                    currentVersion = deviceInfo.version
                )

                // Check for updates with device info
                scope.launch {
                    checkForUpdateWithDeviceInfo(
                        version = deviceInfo.version, "0000"
                    )
                }
            }.onFailure { error ->
                Log.e("FirmwareManager", "Failed to read device info: ${error.message}")
                _state.value = UpdateState.DownloadFailed(context.getString(R.string.check_update_get_device_info_failed))
            }
        }
    }

    private suspend fun checkForUpdateWithDeviceInfo(
        version: String,
        device: String,
    ) {
        try {
            val request = GetFirmwareVersionRequest(
                version = version,
                device = device
            )

            val response = request.execute()
            handleVersionResponse(response)

        } catch (e: Exception) {
            _state.value = UpdateState.DownloadFailed(context.getString(R.string.check_update_get_last_firmware_info_failed))
        }
    }

    private fun handleVersionResponse(response: GetFirmwareVersionRequest.FirmwareVersionResponse) {
        // Check if update is available
        if (!response.hasUpdate) {
            _state.value = UpdateState.NoUpdate
            return
        }

        val firmware = response.firmware
        if (firmware == null) {
            _state.value = UpdateState.DownloadFailed(context.getString(R.string.check_update_download_last_firmware_info_failed))
            return
        }

        // Build download URL with firmware ID
        val downloadURL = API.DOWNLOAD_FIRMWARE + "/${firmware.id}"

        // Build local save path
        val fileSavePath = "ota/glass/${firmware.version}/ota.bin"

        _firmwareInfo.value = FirmwareInfo(
            currentVersion = _firmwareInfo.value.currentVersion,
            newestVersion = firmware.version,
            updateDetails = firmware.releasenotes,
            fileSize = "",  // Size not provided in this API response
            downloadURL = downloadURL,
            fileSavePath = fileSavePath
        )

        _state.value = UpdateState.UpdateAvailable
    }

    /**
     * Download firmware
     */
    fun downloadFirmware() {
        val info = _firmwareInfo.value
        if (info.downloadURL.isEmpty()) {
            _state.value = UpdateState.DownloadFailed(context.getString(R.string.check_update_download_last_firmware_info_failed))
            return
        }

        // Check if file already exists
        val saveFile = getFullFilePath(info.fileSavePath)
        if (saveFile.exists()) {
            localFirmwarePath = saveFile
            _state.value = UpdateState.DownloadCompleted
            return
        }

        _state.value = UpdateState.Downloading
        _downloadProgress.value = 0f

        scope.launch {
            try {
                FirmwareDownloader.download(
                    url = info.downloadURL,
                    savePath = saveFile,
                    onProgress = { _, _, progress ->
                        _downloadProgress.value = progress
                    }
                )

                localFirmwarePath = saveFile
                _state.value = UpdateState.DownloadCompleted
                Log.e("FirmwareManager", "Download completed: ${saveFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("FirmwareManager", "Download failed: ${e.message}")
                _state.value = UpdateState.DownloadFailed(context.getString(R.string.check_update_download_last_firmware_info_failed))
            }
        }
    }

    /**
     * Cancel download
     */
    fun cancelDownload() {
        val info = _firmwareInfo.value
        if (info.fileSavePath.isNotEmpty()) {
            val saveFile = getFullFilePath(info.fileSavePath)
            FirmwareDownloader.cancelDownload(saveFile)
        }
        _state.value = UpdateState.Idle
        _downloadProgress.value = 0f
    }

    /**
     * Clean up downloaded file
     */
    fun cleanupDownloadedFile() {
        localFirmwarePath?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        localFirmwarePath = null
    }

    /**
     * Get local firmware file path (for OTA update)
     */
    fun getLocalFirmwarePath(): File? = localFirmwarePath

    /**
     * Reset state to idle
     */
    fun resetState() {
        _state.value = UpdateState.Idle
        _downloadProgress.value = 0f
    }

    private fun getFullFilePath(relativePath: String): File {
        val context = AiLensApplication.instance.applicationContext
        val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(filesDir, relativePath)
    }

    /**
     * Get version display text
     */
    fun getVersionDisplayText(): String {
        val info = _firmwareInfo.value
        return if (info.newestVersion.isEmpty() || info.newestVersion == info.currentVersion) {
            "V${info.currentVersion}"
        } else {
            "V${info.currentVersion} → V${info.newestVersion}   ${info.fileSize}M"
        }
    }

    /**
     * Get update title text
     */
    fun getUpdateTitleText(): String {
        return if (_state.value == UpdateState.UpdateAvailable) {
            "Update content:"
        } else {
            "Already latest version"
        }
    }
}
