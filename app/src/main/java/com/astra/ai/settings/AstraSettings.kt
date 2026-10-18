package com.astra.ai.settings

import com.astra.ai.core.AstraConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings model for Astra AI.
 */
data class AstraSettings(
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val autoConfirmLowRiskTools: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = true,
    val trustedAutomationEnabled: Boolean = false,
    val rememberPermissionDecisions: Boolean = true,
    val safeguardLevel: String = "BALANCED", // "STRICT", "BALANCED", "TRUSTED_AUTOMATION"
    val trustedActions: Set<String> = emptySet(),
    val developerName: String = AstraConstants.CREATOR_NAME,
    val appVersion: String = AstraConstants.APP_VERSION
)

interface SettingsRepository {
    val settings: StateFlow<AstraSettings>
    fun updateSpeechRate(rate: Float)
    fun updatePitch(pitch: Float)
    fun setAutoConfirmLowRisk(enabled: Boolean)
    fun setHapticFeedback(enabled: Boolean)
    fun setDarkTheme(enabled: Boolean)
    fun setTrustedAutomation(enabled: Boolean)
    fun setRememberPermissionDecisions(enabled: Boolean)
    fun setSafeguardLevel(level: String)
    fun addTrustedAction(actionKey: String)
    fun removeTrustedAction(actionKey: String)
    fun clearTrustedActions()
}

class DefaultSettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AstraSettings())
    override val settings: StateFlow<AstraSettings> = _settings.asStateFlow()

    override fun updateSpeechRate(rate: Float) {
        _settings.value = _settings.value.copy(ttsSpeechRate = rate)
    }

    override fun updatePitch(pitch: Float) {
        _settings.value = _settings.value.copy(ttsPitch = pitch)
    }

    override fun setAutoConfirmLowRisk(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoConfirmLowRiskTools = enabled)
    }

    override fun setHapticFeedback(enabled: Boolean) {
        _settings.value = _settings.value.copy(hapticFeedbackEnabled = enabled)
    }

    override fun setDarkTheme(enabled: Boolean) {
        _settings.value = _settings.value.copy(darkThemeEnabled = enabled)
    }

    override fun setTrustedAutomation(enabled: Boolean) {
        _settings.value = _settings.value.copy(trustedAutomationEnabled = enabled)
    }

    override fun setRememberPermissionDecisions(enabled: Boolean) {
        _settings.value = _settings.value.copy(rememberPermissionDecisions = enabled)
    }

    override fun setSafeguardLevel(level: String) {
        _settings.value = _settings.value.copy(safeguardLevel = level)
    }

    override fun addTrustedAction(actionKey: String) {
        _settings.value = _settings.value.copy(trustedActions = _settings.value.trustedActions + actionKey)
    }

    override fun removeTrustedAction(actionKey: String) {
        _settings.value = _settings.value.copy(trustedActions = _settings.value.trustedActions - actionKey)
    }

    override fun clearTrustedActions() {
        _settings.value = _settings.value.copy(trustedActions = emptySet())
    }
}
