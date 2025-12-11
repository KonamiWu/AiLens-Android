package com.konami.ailens.ble

import android.content.Context
import android.util.Log
import com.konami.ailens.AiLensApplication
import com.konami.ailens.R
import com.konami.ailens.api.FirmwareDownloadURLRequest
import com.konami.ailens.api.GetFirmwareVersionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Centralized firmware management
 * Handles device info, version check, download, and update state
 */
object FirmwareManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val context: Context
        get() = AiLensApplication.instance.applicationContext

    sealed class DownloadState {
        object Idle : DownloadState()
        object CheckingVersion : DownloadState()
        object DownloadAvailable : DownloadState()
        object Downloading : DownloadState()
        object DownloadCompleted : DownloadState()
        data class CheckFailed(val error: String) : DownloadState()
        data class DownloadFailed(val error: String) : DownloadState()
        object NoDownload : DownloadState()
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
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

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
            _state.value = DownloadState.DownloadFailed("Glasses not connected")
            return
        }

        _state.value = DownloadState.CheckingVersion

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
//                    checkForUpdateWithDeviceInfo(version = deviceInfo.version, "0000")
                    checkForUpdateWithDeviceInfo(version = "1.0.0", "0000")
                }
            }.onFailure { error ->
                Log.e("FirmwareManager", "Failed to read device info: ${error.message}")
                _state.value = DownloadState.DownloadFailed(context.getString(R.string.check_update_get_device_info_failed))
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
            _state.value = DownloadState.DownloadFailed(context.getString(R.string.check_update_get_last_firmware_info_failed))
        }
    }

    private suspend fun handleVersionResponse(response: GetFirmwareVersionRequest.FirmwareVersionResponse) {
        // Check if update is available
        if (!response.hasUpdate) {
            _state.value = DownloadState.NoDownload
            return
        }

        val firmware = response.firmware
        if (firmware == null) {
            _state.value = DownloadState.DownloadFailed(context.getString(R.string.check_update_download_last_firmware_info_failed))
            return
        }

        // Get download URL from API
        val downloadURL = withContext(Dispatchers.IO) {
            try {
                val urlRequest = FirmwareDownloadURLRequest("${firmware.id}")
                val urlResponse = urlRequest.execute()
                urlResponse.downloadURL
            } catch (e: Exception) {
                Log.e("FirmwareManager", "Failed to get download URL: ${e.message}")
                ""
            }
        }

        if (downloadURL.isEmpty()) {
            _state.value = DownloadState.DownloadFailed(context.getString(R.string.check_update_download_last_firmware_info_failed))
            return
        }
        Log.e("TAG", "downloadURL = ${downloadURL}")
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

        // Check if file already exists
        val saveFile = getFullFilePath(fileSavePath)
        if (saveFile.exists()) {
            localFirmwarePath = saveFile
            _state.value = DownloadState.DownloadCompleted
            Log.e("FirmwareManager", "Firmware already downloaded: ${saveFile.absolutePath}")
        } else {
            _state.value = DownloadState.DownloadAvailable
        }
    }

    /**
     * Download firmware
     */
    fun downloadFirmware() {
        val info = _firmwareInfo.value
        if (info.downloadURL.isEmpty()) {
            _state.value = DownloadState.DownloadFailed(context.getString(R.string.check_update_download_last_firmware_info_failed))
            return
        }

        // Check if file already exists
        val saveFile = getFullFilePath(info.fileSavePath)
        if (saveFile.exists()) {
            localFirmwarePath = saveFile
            _state.value = DownloadState.DownloadCompleted
            return
        }

        _state.value = DownloadState.Downloading
        _downloadProgress.value = 0f

        scope.launch {
            try {
                download(
                    url = info.downloadURL,
                    savePath = saveFile,
                    onProgress = { _, _, progress ->
                        _downloadProgress.value = progress
                    }
                )

                localFirmwarePath = saveFile
                _state.value = DownloadState.DownloadCompleted
                Log.e("FirmwareManager", "Download completed: ${saveFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("FirmwareManager", "Download failed: ${e.message}")
                _state.value = DownloadState.DownloadFailed(context.getString(R.string.check_update_download_last_firmware_info_failed))
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
            if (saveFile.exists()) {
                saveFile.delete()
                Log.e("FirmwareManager", "Download cancelled and file deleted: ${saveFile.absolutePath}")
            }
        }
        _state.value = DownloadState.Idle
        _downloadProgress.value = 0f
    }

    /**
     * Clean up downloaded file
     */
    fun cleanupDownloadedFile() {
        // Delete entire ota/glass directory
        val otaDir = getFullFilePath("ota/glass")
        if (otaDir.exists() && otaDir.isDirectory) {
            otaDir.deleteRecursively()
            Log.e("FirmwareManager", "Cleaned up OTA directory: ${otaDir.absolutePath}")
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
        _state.value = DownloadState.Idle
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
        return if (_state.value == DownloadState.DownloadAvailable) {
            "Update content:"
        } else {
            "Already latest version"
        }
    }

    /**
     * Download firmware file
     * Reference iOS: LocalSwiftPM/Downloader/Sources/Downloader/Downloader.swift
     */
    private suspend fun download(
        url: String,
        savePath: File,
        onProgress: (Long, Long, Float) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        Log.e("FirmwareManager", "Start downloading: $url")
        Log.e("FirmwareManager", "Save path: ${savePath.absolutePath}")

        // Create parent directory
        savePath.parentFile?.mkdirs()

        var connection: HttpURLConnection? = null
        var outputStream: FileOutputStream? = null

        try {
            // Open connection
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: $responseCode")
            }

            // Get file size
            val fileSize = connection.contentLengthLong
            Log.e("FirmwareManager", "File size: $fileSize bytes")

            val inputStream = connection.inputStream
            outputStream = FileOutputStream(savePath)

            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int

            // Download and write to file
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                // Calculate progress
                val progress = if (fileSize > 0) {
                    totalBytesRead.toFloat() / fileSize.toFloat()
                } else {
                    0f
                }

                // Progress callback
                onProgress(totalBytesRead, fileSize, progress)
            }

            outputStream.flush()
            Log.e("FirmwareManager", "Download completed: ${savePath.absolutePath}")

            savePath

        } catch (e: Exception) {
            Log.e("FirmwareManager", "Download failed: ${e.message}")
            // Delete incomplete file
            if (savePath.exists()) {
                savePath.delete()
            }
            throw e
        } finally {
            outputStream?.close()
            connection?.disconnect()
        }
    }
}
