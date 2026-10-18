package com.astra.ai.automation

/**
 * Fundamental actions executable by the AndroidAutomationEngine.
 */
sealed interface AutomationAction {
    /**
     * Opens an application by name or package identifier.
     */
    data class LaunchApp(val appNameOrPackage: String) : AutomationAction

    /**
     * Taps a semantic element (button, icon, tab, card, or menu item) matching the query.
     */
    data class TapElement(
        val targetQuery: String,
        val optional: Boolean = false
    ) : AutomationAction

    /**
     * Types text into a specific or active editable text field.
     */
    data class EnterText(
        val text: String,
        val targetQuery: String? = null,
        val submit: Boolean = false
    ) : AutomationAction

    /**
     * Scrolls the current screen forward (down) or backward (up).
     */
    data class Scroll(val forward: Boolean = true) : AutomationAction

    /**
     * Navigates back via system global action or navigation stack.
     */
    data class NavigateBack(val count: Int = 1) : AutomationAction

    /**
     * Waits for an element to appear on screen within a specified timeout.
     */
    data class WaitForElement(
        val targetQuery: String,
        val timeoutMs: Long = 3000L
    ) : AutomationAction

    /**
     * Inspects the current screen hierarchy without performing a state-mutating action.
     */
    object InspectScreen : AutomationAction
}
