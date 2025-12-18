package com.konami.ailens.setting

import androidx.lifecycle.ViewModel
import com.konami.ailens.ble.FirmwareManager
import com.konami.ailens.ble.Glasses
import kotlinx.coroutines.flow.StateFlow

/**
 * Check Update ViewModel
 * Simple wrapper around FirmwareManager for UI layer
 */
class CheckUpdateViewModel(private val glasses: Glasses?): ViewModel() {

    // Expose FirmwareManager states
    val state: StateFlow<FirmwareManager.DownloadState> = FirmwareManager.state
    val firmwareInfo: StateFlow<FirmwareManager.FirmwareInfo?> = FirmwareManager.firmwareInfo
    val downloadProgress: StateFlow<Float> = FirmwareManager.downloadProgress

    // Delegate to FirmwareManager
    fun checkForUpdate() = FirmwareManager.checkForUpdate(glasses)
    fun downloadFirmware() = FirmwareManager.downloadFirmware()
    fun cancelDownload() = FirmwareManager.cancelDownload()
    fun getVersionDisplayText() = FirmwareManager.getVersionDisplayText()
    fun getUpdateTitleText() = FirmwareManager.getUpdateTitleText()
}
