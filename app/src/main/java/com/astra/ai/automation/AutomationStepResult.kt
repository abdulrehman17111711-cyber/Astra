package com.astra.ai.automation

import com.astra.ai.accessibility.UiNodeInfo
import com.astra.ai.accessibility.UiObservation

/**
 * Detailed outcome of an individual automation step, recording state before,
 * state after, matched semantic node, execution duration, or precise failure reason.
 */
sealed interface AutomationStepResult {

    val action: AutomationAction
    val isSuccess: Boolean

    data class Success(
        override val action: AutomationAction,
        val message: String,
        val observationBefore: UiObservation? = null,
        val observationAfter: UiObservation? = null,
        val matchedElement: UiNodeInfo? = null,
        val executionTimeMs: Long = 0L
    ) : AutomationStepResult {
        override val isSuccess: Boolean = true
    }

    data class Failed(
        override val action: AutomationAction,
        val reason: String,
        val observationBefore: UiObservation? = null,
        val observationAfter: UiObservation? = null,
        val missingElement: String? = null,
        val matchedElement: UiNodeInfo? = null,
        val isUnsupported: Boolean = false,
        val executionTimeMs: Long = 0L
    ) : AutomationStepResult {
        override val isSuccess: Boolean = false
    }

    data class Skipped(
        override val action: AutomationAction,
        val reason: String
    ) : AutomationStepResult {
        override val isSuccess: Boolean = false
    }
}
