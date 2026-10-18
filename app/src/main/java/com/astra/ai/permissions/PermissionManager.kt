package com.astra.ai.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android permissions supported by Astra AI.
 * Permissions are requested on-demand only when a corresponding feature is triggered.
 */
enum class AstraPermission(
    val manifestPermission: String,
    val title: String,
    val description: String,
    val isCritical: Boolean
) {
    RECORD_AUDIO(
        manifestPermission = Manifest.permission.RECORD_AUDIO,
        title = "Microphone Access",
        description = "Required for speech recognition, wake-word listening, and voice commands.",
        isCritical = true
    ),
    READ_CONTACTS(
        manifestPermission = Manifest.permission.READ_CONTACTS,
        title = "Read Contacts",
        description = "Allows Astra to find contacts by name when you request a call or message.",
        isCritical = false
    ),
    CALL_PHONE(
        manifestPermission = Manifest.permission.CALL_PHONE,
        title = "Direct Calling",
        description = "Allows Astra to initiate phone calls directly upon your confirmation.",
        isCritical = false
    ),
    SEND_SMS(
        manifestPermission = Manifest.permission.SEND_SMS,
        title = "Send SMS",
        description = "Required to dispatch text messages hands-free after explicit user confirmation.",
        isCritical = false
    ),
    POST_NOTIFICATIONS(
        manifestPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else "",
        title = "Notifications",
        description = "Used to notify you when scheduled tasks, reminders, and timers trigger.",
        isCritical = false
    ),
    ACCESS_FINE_LOCATION(
        manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION,
        title = "Precise Location",
        description = "Used for hyper-local weather reports and geo-aware voice answers.",
        isCritical = false
    ),
    ACCESS_COARSE_LOCATION(
        manifestPermission = Manifest.permission.ACCESS_COARSE_LOCATION,
        title = "Approximate Location",
        description = "Used for general city-level context and regional query answers.",
        isCritical = false
    )
}

/**
 * Detailed permission state conforming to Android permission lifecycle.
 */
sealed interface PermissionState {
    data object Granted : PermissionState
    data class Denied(val rationale: String) : PermissionState
    data class PermanentlyDenied(val settingsExplanation: String) : PermissionState
    data class Unavailable(val reason: String) : PermissionState
}

data class PermissionStatus(
    val permission: AstraPermission,
    val isGranted: Boolean,
    val state: PermissionState = if (isGranted) PermissionState.Granted else PermissionState.Denied("Permission not yet granted")
)

data class PermissionRationale(
    val permission: AstraPermission,
    val title: String,
    val explanation: String,
    val featureName: String
)

/**
 * On-demand permission coordinator.
 * Follows Google Play best practices:
 * Never requests all permissions at launch. Only triggers when feature is explicitly requested.
 */
class PermissionManager(private val context: Context) {

    private val permanentlyDeniedSet = mutableSetOf<AstraPermission>()
    private val grantedRuntimeOverrides = mutableSetOf<AstraPermission>()

    private val _permissionsState = MutableStateFlow<List<PermissionStatus>>(emptyList())
    val permissionsState: StateFlow<List<PermissionStatus>> = _permissionsState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val statuses = AstraPermission.entries.map { perm ->
            val granted = isPermissionGranted(perm)
            val state = when {
                perm.manifestPermission.isEmpty() -> PermissionState.Granted
                granted -> PermissionState.Granted
                permanentlyDeniedSet.contains(perm) -> PermissionState.PermanentlyDenied(
                    "Permission was permanently declined. Please enable ${perm.title} in Android System Settings."
                )
                else -> PermissionState.Denied(getRationale(perm).explanation)
            }
            PermissionStatus(perm, granted, state)
        }
        _permissionsState.value = statuses
    }

    fun isPermissionGranted(permission: AstraPermission): Boolean {
        if (permission.manifestPermission.isEmpty()) return true
        if (grantedRuntimeOverrides.contains(permission)) return true
        return ContextCompat.checkSelfPermission(
            context,
            permission.manifestPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks permission status following the recommended flow:
     * Check permission -> Granted / Denied / PermanentlyDenied / Unavailable
     */
    fun checkPermission(permission: AstraPermission): PermissionState {
        if (permission.manifestPermission.isEmpty()) {
            return PermissionState.Granted
        }
        if (isPermissionGranted(permission)) {
            permanentlyDeniedSet.remove(permission)
            return PermissionState.Granted
        }
        if (permanentlyDeniedSet.contains(permission)) {
            return PermissionState.PermanentlyDenied(
                "Please enable '${permission.title}' in Android App Settings to use this feature."
            )
        }
        return PermissionState.Denied(getRationale(permission).explanation)
    }

    /**
     * Returns clear explanation of why the permission is required for the requested feature.
     */
    fun getRationale(permission: AstraPermission): PermissionRationale {
        return when (permission) {
            AstraPermission.RECORD_AUDIO -> PermissionRationale(
                permission = permission,
                title = "Microphone Permission Required",
                explanation = "Astra Voice Assistant requires microphone access to hear your voice commands, process speech in real-time, and detect wake words.",
                featureName = "Voice Recognition"
            )
            AstraPermission.READ_CONTACTS -> PermissionRationale(
                permission = permission,
                title = "Contacts Access Required",
                explanation = "Astra Voice Assistant needs to read contacts on your device to match names like 'Ammi' or 'Ali' when you ask to call or send a message.",
                featureName = "Contact Calling & Messaging"
            )
            AstraPermission.CALL_PHONE -> PermissionRationale(
                permission = permission,
                title = "Phone Calling Required",
                explanation = "Astra Voice Assistant requires phone permission to place outgoing phone calls directly when you confirm.",
                featureName = "Phone Calling"
            )
            AstraPermission.SEND_SMS -> PermissionRationale(
                permission = permission,
                title = "SMS Permission Required",
                explanation = "Astra Voice Assistant needs SMS permission to send confirmed text messages on your behalf without manual typing.",
                featureName = "SMS Messaging"
            )
            AstraPermission.POST_NOTIFICATIONS -> PermissionRationale(
                permission = permission,
                title = "Notifications Required",
                explanation = "Astra Voice Assistant uses notifications to alert you when background tasks, reminders, and timers complete.",
                featureName = "Task Reminders"
            )
            AstraPermission.ACCESS_FINE_LOCATION -> PermissionRationale(
                permission = permission,
                title = "Precise Location Access",
                explanation = "Required to provide accurate weather forecasts and location-sensitive answers for your current coordinates.",
                featureName = "Location Weather & Services"
            )
            AstraPermission.ACCESS_COARSE_LOCATION -> PermissionRationale(
                permission = permission,
                title = "Approximate Location Access",
                explanation = "Allows Astra to understand your general region for local news and weather.",
                featureName = "Regional Awareness"
            )
        }
    }

    /**
     * Records the user's decision from the runtime permission request dialog.
     */
    fun recordPermissionResult(permission: AstraPermission, isGranted: Boolean, shouldShowRationale: Boolean = true) {
        if (isGranted) {
            permanentlyDeniedSet.remove(permission)
            grantedRuntimeOverrides.add(permission)
            AstraLogger.i("PermissionManager", "Permission granted: ${permission.name}")
        } else {
            grantedRuntimeOverrides.remove(permission)
            if (!shouldShowRationale) {
                // If denied and user checked "Don't ask again" or Android denied without rationale
                permanentlyDeniedSet.add(permission)
                AstraLogger.w("PermissionManager", "Permission permanently denied: ${permission.name}")
            } else {
                AstraLogger.i("PermissionManager", "Permission denied: ${permission.name}")
            }
        }
        refresh()
    }

    fun hasAllCriticalPermissions(): Boolean {
        return AstraPermission.entries
            .filter { it.isCritical }
            .all { isPermissionGranted(it) }
    }
}
