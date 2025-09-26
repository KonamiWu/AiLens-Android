package com.konami.ailens.orchestrator.capability

import kotlinx.serialization.Serializable

@Serializable data class VolumeData(val level: Int)
@Serializable data class BrightnessData(val level: Int)
@Serializable data class ScreenModeData(val mode: String)
@Serializable data class DNDData(val enabled: Boolean)
@Serializable data class BatteryData(val level: Int, val isCharging: Boolean)
@Serializable data class LanguageData(val language: String)
@Serializable data class MessageOnly(val message: String)