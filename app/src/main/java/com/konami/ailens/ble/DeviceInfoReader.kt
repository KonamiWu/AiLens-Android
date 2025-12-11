package com.konami.ailens.ble

import android.util.Log
import com.konami.ailens.ble.command.GetDeviceTypeCommand
import com.konami.ailens.ble.command.GetMacAddressCommand
import com.konami.ailens.ble.command.GetSerialNumberCommand
import com.konami.ailens.ble.command.ota.GetVersionListCommand

/**
 * Helper class to read device information from glasses
 * Used for OTA firmware update API requests
 * Reference iOS: FirewareUpdateViewController.swift:420-489
 */
object DeviceInfoReader {

    data class DeviceInfo(
        val version: String,
        val deviceType: String,
        val macAddress: String,
        val serialNumber: String
    )

    /**
     * Read all device information needed for OTA update
     * @param glasses The connected glasses instance
     * @param callback Callback with DeviceInfo or error
     */
    fun readDeviceInfo(glasses: Glasses, callback: (Result<DeviceInfo>) -> Unit) {
        var version = ""
        var deviceType = ""
        var macAddress = ""
        var serialNumber = ""
        var errorOccurred = false

        // 1. Get Version (using GetVersionListCommand and extract main version)
        val getVersionCmd = GetVersionListCommand()
        getVersionCmd.completion = { result ->
            result.onSuccess { versionMap ->
                // Get the version with the highest type value (main version)
                val mainType = versionMap.keys.minOrNull()
                version = if (mainType != null) {
                    versionMap[mainType] ?: "Unknown"
                } else {
                    "Unknown"
                }
                Log.e("DeviceInfoReader", "Got version: $version (type=0x${mainType?.toString(16)})")
            }.onFailure {
                Log.e("DeviceInfoReader", "Failed to get version: ${it.message}")
                errorOccurred = true
                callback(Result.failure(it))
            }
        }
        glasses.add(getVersionCmd)

        // 2. Get Device Type
        val getDeviceTypeCmd = GetDeviceTypeCommand()
        getDeviceTypeCmd.completion = { result ->
            if (!errorOccurred) {
                result.onSuccess {
                    deviceType = it
                    Log.e("DeviceInfoReader", "Got device type: $deviceType")
                }.onFailure {
                    Log.e("DeviceInfoReader", "Failed to get device type: ${it.message}")
                    errorOccurred = true
                    callback(Result.failure(it))
                }
            }
        }
        glasses.add(getDeviceTypeCmd)

        // 3. Get MAC Address
        val getMacCmd = GetMacAddressCommand()
        getMacCmd.completion = { result ->
            if (!errorOccurred) {
                result.onSuccess {
                    macAddress = it
                    Log.e("DeviceInfoReader", "Got MAC: $macAddress")
                }.onFailure {
                    Log.e("DeviceInfoReader", "Failed to get MAC: ${it.message}")
                    errorOccurred = true
                    callback(Result.failure(it))
                }
            }
        }
        glasses.add(getMacCmd)

        // 4. Get Serial Number
        val getSerialCmd = GetSerialNumberCommand()
        getSerialCmd.completion = { result ->
            if (!errorOccurred) {
                result.onSuccess {
                    serialNumber = it
                    Log.e("DeviceInfoReader", "Got serial number: $serialNumber")

                    // All info collected, return result
                    val deviceInfo = DeviceInfo(
                        version = version,
                        deviceType = deviceType,
                        macAddress = macAddress,
                        serialNumber = serialNumber
                    )
                    callback(Result.success(deviceInfo))

                }.onFailure {
                    Log.e("DeviceInfoReader", "Failed to get serial number: ${it.message}")
                    callback(Result.failure(it))
                }
            }
        }
        glasses.add(getSerialCmd)
    }
}
