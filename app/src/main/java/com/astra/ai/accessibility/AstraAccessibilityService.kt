package com.astra.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.astra.ai.core.AstraLogger

/**
 * Transparent, user-controlled Android Accessibility Service for Astra AI.
 *
 * Adheres strictly to security and privacy bounds:
 * - Only active when explicitly enabled by user in Android Settings.
 * - Never captures or transmits sensitive screen data.
 * - Never interacts with password, CAPTCHA, or 2FA controls.
 */
class AstraAccessibilityService : AccessibilityService(), AstraAccessibilityContract {

    companion object {
        private const val TAG = "AstraAccessibility"

        @Volatile
        var instance: AstraAccessibilityService? = null
            private set
    }

    override val isServiceEnabled: Boolean
        get() = instance != null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AccessibilityState.updateConnected(this, true)
        AstraLogger.i(TAG, "AstraAccessibilityService successfully connected and active")

        // Configure runtime capabilities
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.notificationTimeout = 100
            serviceInfo = info
        } catch (e: Exception) {
            AstraLogger.w(TAG, "Error configuring serviceInfo: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val pkgName = event.packageName?.toString()
                if (!pkgName.isNullOrBlank() && pkgName != packageName) {
                    // Cache latest observation in background state
                    try {
                        val root = rootInActiveWindow
                        val observation = UiObservationExtractor.extract(root)
                        AccessibilityState.updateObservation(observation)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onInterrupt() {
        AstraLogger.w(TAG, "AstraAccessibilityService interrupted by system")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AstraLogger.i(TAG, "AstraAccessibilityService unbound")
        instance = null
        AccessibilityState.updateConnected(null, false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        AstraLogger.i(TAG, "AstraAccessibilityService destroyed")
        instance = null
        AccessibilityState.updateConnected(null, false)
    }

    // --- AstraAccessibilityContract implementation ---

    override fun performClick(x: Float, y: Float): Boolean {
        // Coordinate click fallback
        AstraLogger.w(TAG, "Coordinate click requested at ($x, $y) - coordinate strategy is last resort")
        return false
    }

    override fun performScroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = UiNodeFinder.findScrollableContainer(root) ?: return false
        val action = if (forward) {
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return scrollable.performAction(action)
    }

    override fun readCurrentScreenHierarchy(): String? {
        val root = rootInActiveWindow ?: return null
        val observation = UiObservationExtractor.extract(root)
        return observation.summary()
    }
}
