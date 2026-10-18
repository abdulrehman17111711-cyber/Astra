package com.astra.ai.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.astra.ai.accessibility.AccessibilityState
import com.astra.ai.accessibility.AstraAccessibilityService
import com.astra.ai.accessibility.UiNodeFinder
import com.astra.ai.accessibility.UiObservation
import com.astra.ai.accessibility.UiObservationExtractor
import com.astra.ai.apps.AppLaunchResult
import com.astra.ai.apps.AppManager
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AndroidAutomationEngine
 * Reusable Android automation engine utilizing public Android APIs, intents,
 * and user-enabled AccessibilityService.
 *
 * Core capabilities:
 * - Dynamic app discovery and launch
 * - Accessible UI node inspection (buttons, text fields, menus, cards, icons)
 * - Semantic element resolution without brittle hardcoded screen coordinates
 * - Text entry, tapping, scrolling, navigation
 * - Mandatory reinspection after every action to verify state
 * - Multi-step execution planning with automatic error recovery and missing element detection
 * - Zero security bypassing: Strictly blocks password/credential fields
 */
class AndroidAutomationEngine(
    private val context: Context,
    private val appManager: AppManager
) {
    companion object {
        private const val TAG = "AutomationEngine"
        private const val DEFAULT_ACTION_DELAY_MS = 300L
        private const val STEP_TIMEOUT_MS = 6000L
    }

    /**
     * Inspects the current screen hierarchy via Android Accessibility.
     */
    fun inspectScreen(): UiObservation {
        val service = getActiveServiceOrNull()
            ?: return UiObservation(
                packageName = null,
                error = "Astra Accessibility Service is not enabled. Please enable it in Android Settings > Accessibility > Astra Voice Assistant Accessibility."
            )

        val root = try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            AstraLogger.w(TAG, "Error obtaining root node: ${e.message}")
            null
        }

        val observation = UiObservationExtractor.extract(root)
        AccessibilityState.updateObservation(observation)
        return observation
    }

    /**
     * Opens an installed application by display name or package identifier.
     * Detects if app is missing and never fakes success.
     */
    suspend fun openApp(appNameOrPackage: String): AutomationStepResult {
        val startTime = System.currentTimeMillis()
        val action = AutomationAction.LaunchApp(appNameOrPackage)
        val observationBefore = inspectScreen()

        val trimmed = appNameOrPackage.trim()
        if (trimmed.isBlank()) {
            return AutomationStepResult.Failed(
                action = action,
                reason = "Application name or package must not be empty.",
                observationBefore = observationBefore,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val launchResult = appManager.launchAppDetailed(trimmed)
        val elapsed = System.currentTimeMillis() - startTime

        return when (launchResult) {
            is AppLaunchResult.Success -> {
                // Wait briefly for the launched app window to stabilize
                delay(600)
                val observationAfter = inspectScreen()
                AutomationStepResult.Success(
                    action = action,
                    message = launchResult.message,
                    observationBefore = observationBefore,
                    observationAfter = observationAfter,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
            is AppLaunchResult.NotInstalled -> {
                AutomationStepResult.Failed(
                    action = action,
                    reason = "\"${launchResult.requestedName}\" is not installed on this device.",
                    observationBefore = observationBefore,
                    isUnsupported = false,
                    executionTimeMs = elapsed
                )
            }
            is AppLaunchResult.CannotLaunch -> {
                AutomationStepResult.Failed(
                    action = action,
                    reason = launchResult.message,
                    observationBefore = observationBefore,
                    isUnsupported = true,
                    executionTimeMs = elapsed
                )
            }
            is AppLaunchResult.Error -> {
                AutomationStepResult.Failed(
                    action = action,
                    reason = launchResult.message,
                    observationBefore = observationBefore,
                    executionTimeMs = elapsed
                )
            }
        }
    }

    /**
     * Taps a semantic element on screen with recovery and state verification.
     */
    suspend fun tapElement(targetQuery: String, optional: Boolean = false): AutomationStepResult {
        val startTime = System.currentTimeMillis()
        val action = AutomationAction.TapElement(targetQuery, optional)
        val observationBefore = inspectScreen()

        val service = getActiveServiceOrNull()
        if (service == null) {
            return AutomationStepResult.Failed(
                action = action,
                reason = "Accessibility service is disabled. Cannot interact with on-screen UI without user permission in Android Settings.",
                observationBefore = observationBefore,
                isUnsupported = true,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val executed = withTimeoutOrNull(STEP_TIMEOUT_MS) {
            // 1. Initial semantic lookup
            var root = getRootNodeSafely(service)
            var match = UiNodeFinder.findBestMatch(root, targetQuery)

            // 2. Recovery attempt: Expanded synonyms
            if (match == null) {
                val expanded = UiNodeFinder.expandSemanticTerms(targetQuery)
                for (term in expanded) {
                    match = UiNodeFinder.findBestMatch(root, term)
                    if (match != null) break
                }
            }

            // 3. Recovery attempt: Scroll forward once if control is below fold
            if (match == null) {
                val scrollable = UiNodeFinder.findScrollableContainer(root)
                if (scrollable != null && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    delay(DEFAULT_ACTION_DELAY_MS)
                    root = getRootNodeSafely(service)
                    match = UiNodeFinder.findBestMatch(root, targetQuery)
                }
            }

            if (match == null) {
                return@withTimeoutOrNull AutomationStepResult.Failed(
                    action = action,
                    reason = "Could not find \"$targetQuery\" on the current screen.",
                    observationBefore = observationBefore,
                    missingElement = targetQuery,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Security constraint: NEVER tap password/auth controls
            if (match.snapshot.isPassword) {
                return@withTimeoutOrNull AutomationStepResult.Failed(
                    action = action,
                    reason = "Security violation: Astra is prohibited from interacting with password or credential fields.",
                    observationBefore = observationBefore,
                    isUnsupported = true,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Perform click on node or nearest clickable ancestor
            val clickableNode = findClickableAncestorOrSelf(match.node)
            val clicked = clickableNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false

            delay(DEFAULT_ACTION_DELAY_MS)
            val observationAfter = inspectScreen()

            if (clicked) {
                AutomationStepResult.Success(
                    action = action,
                    message = "Tapped \"${match.snapshot.displayLabel}\".",
                    observationBefore = observationBefore,
                    observationAfter = observationAfter,
                    matchedElement = match.snapshot,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                AutomationStepResult.Failed(
                    action = action,
                    reason = "Found element \"${match.snapshot.displayLabel}\", but the click action was rejected by Android.",
                    observationBefore = observationBefore,
                    observationAfter = observationAfter,
                    matchedElement = match.snapshot,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }

        return executed ?: AutomationStepResult.Failed(
            action = action,
            reason = "Timed out waiting to tap \"$targetQuery\".",
            observationBefore = observationBefore,
            missingElement = targetQuery,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Types text into a targeted or focused input field, with optional submission.
     */
    suspend fun enterText(
        text: String,
        targetQuery: String? = null,
        submit: Boolean = false
    ): AutomationStepResult {
        val startTime = System.currentTimeMillis()
        val action = AutomationAction.EnterText(text, targetQuery, submit)
        val observationBefore = inspectScreen()

        val service = getActiveServiceOrNull()
        if (service == null) {
            return AutomationStepResult.Failed(
                action = action,
                reason = "Accessibility service is disabled. Cannot enter text without accessibility permission.",
                observationBefore = observationBefore,
                isUnsupported = true,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val executed = withTimeoutOrNull(STEP_TIMEOUT_MS) {
            val root = getRootNodeSafely(service)
            val targetNode: AccessibilityNodeInfo? = if (!targetQuery.isNullOrBlank()) {
                val match = UiNodeFinder.findBestMatch(root, targetQuery)
                match?.node
            } else {
                UiNodeFinder.findSearchInputField(root) ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            }

            if (targetNode == null) {
                return@withTimeoutOrNull AutomationStepResult.Failed(
                    action = action,
                    reason = "No accessible text field found on screen.",
                    observationBefore = observationBefore,
                    missingElement = targetQuery ?: "Input field",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            if (targetNode.isPassword) {
                return@withTimeoutOrNull AutomationStepResult.Failed(
                    action = action,
                    reason = "Security violation: Astra is prohibited from entering credentials or passwords.",
                    observationBefore = observationBefore,
                    isUnsupported = true,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val typed = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            delay(DEFAULT_ACTION_DELAY_MS)

            // If submit requested, attempt to click search or send button
            if (typed && submit) {
                val submitRoot = getRootNodeSafely(service)
                val submitMatch = UiNodeFinder.findBestMatch(submitRoot, "search")
                    ?: UiNodeFinder.findBestMatch(submitRoot, "send")
                    ?: UiNodeFinder.findBestMatch(submitRoot, "go")
                submitMatch?.node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(DEFAULT_ACTION_DELAY_MS)
            }

            val observationAfter = inspectScreen()

            if (typed) {
                AutomationStepResult.Success(
                    action = action,
                    message = "Entered \"$text\" into input field.",
                    observationBefore = observationBefore,
                    observationAfter = observationAfter,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                AutomationStepResult.Failed(
                    action = action,
                    reason = "Failed setting text via Android Accessibility framework.",
                    observationBefore = observationBefore,
                    observationAfter = observationAfter,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }

        return executed ?: AutomationStepResult.Failed(
            action = action,
            reason = "Timed out entering text.",
            observationBefore = observationBefore,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Scrolls the screen in the specified direction.
     */
    suspend fun scroll(forward: Boolean = true): AutomationStepResult {
        val startTime = System.currentTimeMillis()
        val action = AutomationAction.Scroll(forward)
        val observationBefore = inspectScreen()

        val service = getActiveServiceOrNull()
        if (service == null) {
            return AutomationStepResult.Failed(
                action = action,
                reason = "Accessibility service is disabled.",
                observationBefore = observationBefore,
                isUnsupported = true,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val root = getRootNodeSafely(service)
        val scrollable = UiNodeFinder.findScrollableContainer(root)
        if (scrollable == null) {
            return AutomationStepResult.Failed(
                action = action,
                reason = "No scrollable container found on current screen.",
                observationBefore = observationBefore,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val scrollAction = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }

        val scrolled = scrollable.performAction(scrollAction)
        delay(DEFAULT_ACTION_DELAY_MS)
        val observationAfter = inspectScreen()

        return if (scrolled) {
            AutomationStepResult.Success(
                action = action,
                message = if (forward) "Scrolled screen forward." else "Scrolled screen backward.",
                observationBefore = observationBefore,
                observationAfter = observationAfter,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } else {
            AutomationStepResult.Failed(
                action = action,
                reason = "Scroll action was not performed by container.",
                observationBefore = observationBefore,
                observationAfter = observationAfter,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Navigates back via system global action.
     */
    suspend fun navigateBack(count: Int = 1): AutomationStepResult {
        val startTime = System.currentTimeMillis()
        val action = AutomationAction.NavigateBack(count)
        val observationBefore = inspectScreen()

        val service = getActiveServiceOrNull()
        if (service == null) {
            return AutomationStepResult.Failed(
                action = action,
                reason = "Accessibility service is disabled.",
                observationBefore = observationBefore,
                isUnsupported = true,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        var allSucceeded = true
        for (i in 0 until count) {
            val back = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            if (!back) allSucceeded = false
            delay(250)
        }

        val observationAfter = inspectScreen()
        return if (allSucceeded) {
            AutomationStepResult.Success(
                action = action,
                message = "Navigated back $count time(s).",
                observationBefore = observationBefore,
                observationAfter = observationAfter,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } else {
            AutomationStepResult.Failed(
                action = action,
                reason = "Could not navigate back via Accessibility global action.",
                observationBefore = observationBefore,
                observationAfter = observationAfter,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Waits for an element matching targetQuery to appear on screen.
     */
    suspend fun waitForElement(targetQuery: String, timeoutMs: Long = 3000L): AutomationStepResult {
        val startTime = System.currentTimeMillis()
        val action = AutomationAction.WaitForElement(targetQuery, timeoutMs)
        val observationBefore = inspectScreen()

        val service = getActiveServiceOrNull()
        if (service == null) {
            return AutomationStepResult.Failed(
                action = action,
                reason = "Accessibility service is disabled.",
                observationBefore = observationBefore,
                isUnsupported = true,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val intervalMs = 250L
        val maxAttempts = (timeoutMs / intervalMs).coerceAtLeast(1)

        for (i in 0 until maxAttempts) {
            val root = getRootNodeSafely(service)
            val match = UiNodeFinder.findBestMatch(root, targetQuery)
            if (match != null) {
                val observationAfter = inspectScreen()
                return AutomationStepResult.Success(
                    action = action,
                    message = "Element \"${match.snapshot.displayLabel}\" appeared on screen.",
                    observationBefore = observationBefore,
                    observationAfter = observationAfter,
                    matchedElement = match.snapshot,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
            delay(intervalMs)
        }

        return AutomationStepResult.Failed(
            action = action,
            reason = "Timed out after ${timeoutMs}ms waiting for \"$targetQuery\" to appear.",
            observationBefore = observationBefore,
            missingElement = targetQuery,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Executes a planned multi-step automation workflow.
     * Evaluates results sequentially, stops on critical failure, and summarizes outcome.
     */
    suspend fun executePlan(goal: String, actions: List<AutomationAction>): AutomationExecutionReport {
        val planStartTime = System.currentTimeMillis()
        val stepResults = mutableListOf<AutomationStepResult>()
        var overallSuccess = true
        var failureSummary = ""

        AstraLogger.i(TAG, "Executing plan with ${actions.size} steps for goal: \"$goal\"")

        for ((index, action) in actions.withIndex()) {
            val result = when (action) {
                is AutomationAction.LaunchApp -> openApp(action.appNameOrPackage)
                is AutomationAction.TapElement -> tapElement(action.targetQuery, action.optional)
                is AutomationAction.EnterText -> enterText(action.text, action.targetQuery, action.submit)
                is AutomationAction.Scroll -> scroll(action.forward)
                is AutomationAction.NavigateBack -> navigateBack(action.count)
                is AutomationAction.WaitForElement -> waitForElement(action.targetQuery, action.timeoutMs)
                is AutomationAction.InspectScreen -> {
                    val obs = inspectScreen()
                    AutomationStepResult.Success(
                        action = action,
                        message = "Screen inspected: ${obs.packageName ?: "unknown"}",
                        observationAfter = obs,
                        executionTimeMs = 50L
                    )
                }
            }

            stepResults.add(result)

            if (!result.isSuccess) {
                // If action is marked optional, continue; otherwise halt
                val isOptional = (action as? AutomationAction.TapElement)?.optional ?: false
                if (!isOptional) {
                    overallSuccess = false
                    failureSummary = (result as? AutomationStepResult.Failed)?.reason
                        ?: "Step ${index + 1} failed."
                    AstraLogger.w(TAG, "Step ${index + 1} failed: $failureSummary. Halting execution.")
                    break
                }
            }
        }

        val totalTime = System.currentTimeMillis() - planStartTime
        val successfulCount = stepResults.count { it.isSuccess }
        val finalObservation = stepResults.lastOrNull()?.let {
            when (it) {
                is AutomationStepResult.Success -> it.observationAfter
                is AutomationStepResult.Failed -> it.observationAfter ?: it.observationBefore
                is AutomationStepResult.Skipped -> null
            }
        } ?: inspectScreen()

        val summary = if (overallSuccess) {
            "Completed \"$goal\" successfully ($successfulCount/${actions.size} steps)."
        } else {
            "Could not complete \"$goal\": $failureSummary"
        }

        return AutomationExecutionReport(
            goal = goal,
            totalSteps = actions.size,
            executedSteps = stepResults,
            isSuccess = overallSuccess,
            summary = summary,
            finalObservation = finalObservation,
            executionTimeMs = totalTime
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
}
