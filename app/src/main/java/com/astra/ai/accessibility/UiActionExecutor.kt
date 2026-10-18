package com.astra.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.delay

/**
 * Result of an accessibility UI action.
 */
data class UiActionResult(
    val success: Boolean,
    val message: String,
    val observationAfter: UiObservation? = null,
    val matchedNode: UiNodeInfo? = null
)

/**
 * Executes UI actions through Android's Accessibility framework with semantic matching,
 * state verification, error recovery, and strict security boundaries.
 */
object UiActionExecutor {

    private const val TAG = "UiActionExecutor"
    private const val ACTION_DELAY_MS = 350L

    /**
     * Captures a snapshot of the current active screen.
     */
    fun observeCurrentScreen(): UiObservation {
        val service = AccessibilityState.serviceInstance
            ?: return UiObservation(packageName = null)

        val root = try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            AstraLogger.w(TAG, "Failed obtaining root window: ${e.message}")
            null
        }

        val observation = UiObservationExtractor.extract(root)
        AccessibilityState.updateObservation(observation)
        return observation
    }

    /**
     * Clicks a target control matching the query semantic.
     */
    suspend fun click(targetQuery: String): UiActionResult {
        val service = getActiveServiceOrNull()
            ?: return serviceDisabledResult()

        AstraLogger.i(TAG, "Attempting click on target: \"$targetQuery\"")

        // 1. Initial lookup
        var root = getRootNodeSafely(service)
        var match = UiNodeFinder.findBestMatch(root, targetQuery)

        // 2. Error Recovery: Inspect again or check alternate semantic queries
        if (match == null) {
            delay(200)
            root = getRootNodeSafely(service)
            val expanded = UiNodeFinder.expandSemanticTerms(targetQuery)
            for (alt in expanded) {
                match = UiNodeFinder.findBestMatch(root, alt)
                if (match != null) break
            }
        }

        // 3. Error Recovery: Scroll check if off-screen
        if (match == null) {
            val scrollable = UiNodeFinder.findScrollableContainer(root)
            if (scrollable != null && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                delay(ACTION_DELAY_MS)
                root = getRootNodeSafely(service)
                match = UiNodeFinder.findBestMatch(root, targetQuery)
            }
        }

        if (match == null) {
            return UiActionResult(
                success = false,
                message = "This app does not expose the required \"$targetQuery\" control to Android Accessibility."
            )
        }

        // Security enforcement
        if (match.snapshot.isPassword) {
            return securityBlockedResult("password/credential field")
        }

        // Perform click
        val nodeToClick = findClickableAncestorOrSelf(match.node)
        val clicked = nodeToClick?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false

        if (!clicked) {
            return UiActionResult(
                success = false,
                message = "Found control \"${match.snapshot.displayLabel}\", but Android Accessibility could not click it."
            )
        }

        // State verification after action
        delay(ACTION_DELAY_MS)
        val observationAfter = observeCurrentScreen()
        AccessibilityState.updateLastAction("Clicked: ${match.snapshot.displayLabel}")

        return UiActionResult(
            success = true,
            message = "Clicked \"${match.snapshot.displayLabel}\" successfully.",
            observationAfter = observationAfter,
            matchedNode = match.snapshot
        )
    }

    /**
     * Long clicks a target control matching the query.
     */
    suspend fun longClick(targetQuery: String): UiActionResult {
        val service = getActiveServiceOrNull() ?: return serviceDisabledResult()
        var root = getRootNodeSafely(service)
        val match = UiNodeFinder.findBestMatch(root, targetQuery)
            ?: return UiActionResult(
                success = false,
                message = "This app does not expose the required \"$targetQuery\" control to Android Accessibility."
            )

        if (match.snapshot.isPassword) return securityBlockedResult("password")

        val target = findClickableAncestorOrSelf(match.node)
        val success = target?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) ?: false

        delay(ACTION_DELAY_MS)
        val observationAfter = observeCurrentScreen()

        return UiActionResult(
            success = success,
            message = if (success) "Long-clicked \"${match.snapshot.displayLabel}\"." else "Failed to long-click control.",
            observationAfter = observationAfter,
            matchedNode = match.snapshot
        )
    }

    /**
     * Types text into a targeted input field or currently focused editable field.
     */
    suspend fun typeText(text: String, targetQuery: String? = null): UiActionResult {
        val service = getActiveServiceOrNull() ?: return serviceDisabledResult()
        val root = getRootNodeSafely(service)

        val targetNode: AccessibilityNodeInfo? = if (!targetQuery.isNullOrBlank()) {
            val match = UiNodeFinder.findBestMatch(root, targetQuery)
            if (match != null && match.snapshot.isPassword) {
                return securityBlockedResult("password field")
            }
            match?.node
        } else {
            UiNodeFinder.findSearchInputField(root) ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        if (targetNode == null) {
            return UiActionResult(
                success = false,
                message = "This app does not expose an accessible text input field."
            )
        }

        if (targetNode.isPassword) {
            return securityBlockedResult("password field")
        }

        // Set text via AccessibilityNodeInfo ACTION_SET_TEXT
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val typed = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        delay(ACTION_DELAY_MS)
        val observationAfter = observeCurrentScreen()
        AccessibilityState.updateLastAction("Typed text: $text")

        return UiActionResult(
            success = typed,
            message = if (typed) "Entered \"$text\" into input field." else "Failed setting text via Android Accessibility.",
            observationAfter = observationAfter
        )
    }

    /**
     * Scrolls the current screen forward (down).
     */
    suspend fun scrollForward(): UiActionResult {
        val service = getActiveServiceOrNull() ?: return serviceDisabledResult()
        val root = getRootNodeSafely(service)
        val scrollable = UiNodeFinder.findScrollableContainer(root)

        val scrolled = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
        delay(ACTION_DELAY_MS)
        val observationAfter = observeCurrentScreen()

        return UiActionResult(
            success = scrolled,
            message = if (scrolled) "Scrolled screen forward." else "No scrollable container found on current screen.",
            observationAfter = observationAfter
        )
    }

    /**
     * Scrolls the current screen backward (up).
     */
    suspend fun scrollBackward(): UiActionResult {
        val service = getActiveServiceOrNull() ?: return serviceDisabledResult()
        val root = getRootNodeSafely(service)
        val scrollable = UiNodeFinder.findScrollableContainer(root)

        val scrolled = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) ?: false
        delay(ACTION_DELAY_MS)
        val observationAfter = observeCurrentScreen()

        return UiActionResult(
            success = scrolled,
            message = if (scrolled) "Scrolled screen backward." else "No scrollable container found on current screen.",
            observationAfter = observationAfter
        )
    }

    /**
     * Presses the system Back button via Accessibility GLOBAL_ACTION_BACK.
     */
    suspend fun goBack(): UiActionResult {
        val service = getActiveServiceOrNull() ?: return serviceDisabledResult()
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(ACTION_DELAY_MS)
        val observationAfter = observeCurrentScreen()

        return UiActionResult(
            success = success,
            message = if (success) "Navigated back." else "Failed to perform Back navigation.",
            observationAfter = observationAfter
        )
    }

    /**
     * High-level automation workflow:
     * 1. Find and click search control
     * 2. Wait and inspect screen
     * 3. Enter text
     * 4. Verify result
     */
    suspend fun searchAndEnterQuery(searchControlQuery: String, queryText: String): UiActionResult {
        val clickResult = click(searchControlQuery)
        if (!clickResult.success) {
            return clickResult
        }

        delay(400)
        val typeResult = typeText(queryText)
        if (!typeResult.success) {
            return typeResult
        }

        delay(300)
        // Optionally submit if enter/search button exists
        val submitResult = click("search")
        val finalObservation = observeCurrentScreen()

        return UiActionResult(
            success = true,
            message = "Searched for \"$queryText\" successfully.",
            observationAfter = finalObservation
        )
    }

    private fun findClickableAncestorOrSelf(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return node
    }

    private fun getActiveServiceOrNull(): AstraAccessibilityService? {
        return AccessibilityState.serviceInstance
    }

    private fun getRootNodeSafely(service: AstraAccessibilityService): AccessibilityNodeInfo? {
        return try {
            service.rootInActiveWindow
        } catch (_: Exception) {
            null
        }
    }

    private fun serviceDisabledResult(): UiActionResult {
        return UiActionResult(
            success = false,
            message = "Astra Accessibility Service is not enabled. Please enable it in Android Settings > Accessibility > Astra AI."
        )
    }

    private fun securityBlockedResult(fieldDesc: String): UiActionResult {
        return UiActionResult(
            success = false,
            message = "Security policy violation: Astra AI is prohibited from interacting with $fieldDesc or authentication systems."
        )
    }
}
