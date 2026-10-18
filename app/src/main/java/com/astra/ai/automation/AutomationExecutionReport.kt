package com.astra.ai.automation

import com.astra.ai.accessibility.UiObservation

/**
 * Complete summary report of a multi-step automation task executed by AndroidAutomationEngine.
 */
data class AutomationExecutionReport(
    val goal: String,
    val totalSteps: Int,
    val executedSteps: List<AutomationStepResult>,
    val isSuccess: Boolean,
    val summary: String,
    val finalObservation: UiObservation? = null,
    val executionTimeMs: Long = 0L
)
