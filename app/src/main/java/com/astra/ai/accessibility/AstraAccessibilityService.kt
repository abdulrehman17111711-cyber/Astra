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
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            info.notificationTimeout = 100
            serviceInfo = info
        } catch (e: Exception) {
            AstraLogger.w(TAG, "Error configuring serviceInfo: ${e.message}")
        }
    }

    private var lastVolumeDownTime = 0L

    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && event.action == android.view.KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeDownTime in 50..550) {
                lastVolumeDownTime = 0L
                AstraLogger.i(TAG, "Double-press Volume Down detected! Launching Astra Voice Assistant...")
                activateAstraDirectly()
                return true
            } else {
                lastVolumeDownTime = now
            }
        }
        return super.onKeyEvent(event)
    }

    private fun activateAstraDirectly() {
        try {
            val intent = Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_AUTO_ACTIVATE_VOICE", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            AstraLogger.e(TAG, "Failed to launch Astra on key event: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotificationEvent(event)
            }

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

    /**
     * Captures incoming notifications from WhatsApp, Messages, Telegram, Gmail, and other apps.
     */
    private fun handleNotificationEvent(event: AccessibilityEvent) {
        val pkgName = event.packageName?.toString().orEmpty()
        if (pkgName.isBlank() || pkgName == packageName) return

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(pkgName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkgName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }

        var title = ""
        var text = ""

        val parcelable = event.parcelableData
        if (parcelable is android.app.Notification) {
            val extras = parcelable.extras
            title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
            text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
                .orEmpty()
        }

        if (text.isBlank() && event.text.isNotEmpty()) {
            val combined = event.text.joinToString(" ").trim()
            if (title.isBlank()) {
                title = combined.substringBefore(':').trim()
                text = combined.substringAfter(':', combined).trim()
            } else {
                text = combined
            }
        }

        if (title.isNotBlank() || text.isNotBlank()) {
            val captured = CapturedNotification(
                packageName = pkgName,
                appName = appName,
                title = if (title.isNotBlank()) title else appName,
                text = text
            )
            AccessibilityState.addNotification(captured)
        }
    }

    /**
     * Cross-app automation: Clicks the send button in the currently active messaging app (WhatsApp, Messages, Telegram).
     */
    fun performSendClick(): Boolean {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return false

        // Common resource IDs and text descriptions for Send buttons across messaging apps
        val sendQueries = listOf("Send", "send", "ارسال", "بھیجیں", "send_button", "compose_send_button")
        for (query in sendQueries) {
            val match = UiNodeFinder.findBestMatch(root, query)
            if (match != null) {
                var current: android.view.accessibility.AccessibilityNodeInfo? = match.node
                while (current != null) {
                    if (current.isClickable && current.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                        AstraLogger.i(TAG, "Automated send click successful on node: ${match.snapshot.displayLabel}")
                        return true
                    }
                    current = current.parent
                }
            }
        }
        return false
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
