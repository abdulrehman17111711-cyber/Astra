package com.astra.ai.tools.builtin

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParameter

/**
 * Tool: device_unlock
 * Handles waking up the display, dismissing non-secure keyguards, and bringing Astra to foreground.
 */
class DeviceUnlockTool(
    private val context: Context
) : Tool {

    override val name: String = "device_unlock"

    override val description: String =
        "Wakes the display, turns on the screen, and requests dismissal of keyguard/lock screen."

    override val parameters: List<ToolParameter> = emptyList()

    override val requiredPermissions: List<String> = emptyList()

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

            // 1. Wake the screen
            val isScreenOn = powerManager?.isInteractive ?: false
            if (powerManager != null && !isScreenOn) {
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "Astra:WakeScreenLock"
                )
                wakeLock.acquire(3000L)
            }

            // 2. Launch / Bring MainActivity to front with unlock request
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("action", "request_unlock")
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }

            val isDeviceLocked = keyguardManager?.isDeviceLocked ?: false
            val isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false

            val message = when {
                !isKeyguardLocked -> "Screen turned on and device is unlocked. Astra is ready."
                isDeviceLocked -> "Screen turned on. Device has a secure lock (PIN/Fingerprint); please scan your fingerprint or enter PIN to complete unlock."
                else -> "Screen turned on. Dismissing lock screen."
            }

            ToolExecutionResult.success(
                displayText = message,
                output = mapOf(
                    "screenOn" to true,
                    "keyguardLocked" to isKeyguardLocked,
                    "deviceSecure" to isDeviceLocked
                ),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            AstraLogger.e("DeviceUnlockTool", "Error during unlock: ${e.message}")
            ToolExecutionResult.failure("Failed waking/unlocking device: ${e.message}")
        }
    }
}
