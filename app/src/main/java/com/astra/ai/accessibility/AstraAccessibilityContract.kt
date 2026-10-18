package com.astra.ai.accessibility

/**
 * Foundational contract for Astra AI accessibility subsystem.
 * Future phases will use this to automate UI, read on-screen content, and interact with other apps.
 */
interface AstraAccessibilityContract {
    val isServiceEnabled: Boolean

    fun performClick(x: Float, y: Float): Boolean
    fun performScroll(forward: Boolean): Boolean
    fun readCurrentScreenHierarchy(): String?
}

class DefaultAccessibilityContract : AstraAccessibilityContract {
    override val isServiceEnabled: Boolean = false

    override fun performClick(x: Float, y: Float): Boolean = false

    override fun performScroll(forward: Boolean): Boolean = false

    override fun readCurrentScreenHierarchy(): String? = null
}
