package com.astra.ai.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Reactive state model capturing the runtime status of Astra AI's accessibility service.
 */
data class AccessibilityStateModel(
    val isServiceConnected: Boolean = false,
    val isSettingsEnabled: Boolean = false,
    val activePackage: String? = null,
    val nodeCount: Int = 0,
    val lastAction: String? = null,
    val lastObservation: UiObservation? = null,
    val lastUpdatedMs: Long = System.currentTimeMillis()
) {
    val isFullyActive: Boolean
        get() = isServiceConnected && isSettingsEnabled
}

/**
 * Central state and helper coordinator for Astra's Accessibility subsystem.
 */
object AccessibilityState {

    private const val TAG = "AccessibilityState"
    private const val SERVICE_CLASS_NAME = "com.astra.ai.accessibility.AstraAccessibilityService"

    private val _state = MutableStateFlow(AccessibilityStateModel())
    val state: StateFlow<AccessibilityStateModel> = _state.asStateFlow()

    @Volatile
    var serviceInstance: AstraAccessibilityService? = null
        private set

    /**
     * Checks whether Astra's Accessibility Service is currently enabled in Android Settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false

            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val isEnabled = enabledServices.any {
                val serviceInfo = it.resolveInfo.serviceInfo
                serviceInfo.packageName == context.packageName &&
                        serviceInfo.name.contains("AstraAccessibilityService")
            }
            isEnabled
        } catch (e: Exception) {
            AstraLogger.w(TAG, "Failed checking accessibility enabled state: ${e.message}")
            false
        }
    }

    /**
     * Launches the Android Accessibility Settings screen for the user.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AstraLogger.e(TAG, "Failed opening accessibility settings: ${e.message}", e)
        }
    }

    /**
     * Updates the connection status from the service lifecycle.
     */
    fun updateConnected(service: AstraAccessibilityService?, connected: Boolean) {
        serviceInstance = if (connected) service else null
        _state.update { current ->
            current.copy(
                isServiceConnected = connected,
                lastUpdatedMs = System.currentTimeMillis()
            )
        }
        AstraLogger.i(TAG, "Accessibility service connected status changed to: $connected")
    }

    /**
     * Updates the system enabled status from checks.
     */
    fun refreshSystemStatus(context: Context) {
        val enabled = isAccessibilityServiceEnabled(context)
        _state.update { current ->
            current.copy(
                isSettingsEnabled = enabled,
                lastUpdatedMs = System.currentTimeMillis()
            )
        }
    }

    /**
     * Updates the active screen observation.
     */
    fun updateObservation(observation: UiObservation) {
        _state.update { current ->
            current.copy(
                activePackage = observation.packageName,
                nodeCount = observation.allNodes().size,
                lastObservation = observation,
                lastUpdatedMs = System.currentTimeMillis()
            )
        }
    }

    /**
     * Updates the record of the last action executed.
     */
    fun updateLastAction(action: String) {
        _state.update { current ->
            current.copy(
                lastAction = action,
                lastUpdatedMs = System.currentTimeMillis()
            )
        }
    }
}
