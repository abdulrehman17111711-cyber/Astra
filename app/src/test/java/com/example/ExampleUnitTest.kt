package com.example

import com.astra.ai.ai.AiContext
import com.astra.ai.ai.AiReasoningResult
import com.astra.ai.ai.DefaultAiEngine
import com.astra.ai.assistant.AssistantStateType
import com.astra.ai.tools.ToolRegistry
import com.astra.ai.tools.builtin.WorldClockTool
import com.astra.ai.voice.WakeActivationConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testToolRegistryDynamicRegistration() {
        val registry = ToolRegistry()
        val clockTool = WorldClockTool()
        registry.register(clockTool)

        assertTrue(registry.hasTool("world_clock"))
        assertNotNull(registry.getTool("world_clock"))
        assertEquals(1, registry.getAllTools().size)

        val unregistered = registry.unregister("world_clock")
        assertNotNull(unregistered)
        assertEquals(0, registry.getAllTools().size)
    }

    @Test
    fun testWorldClockToolExecution() = runBlocking {
        val clockTool = WorldClockTool()
        val result = clockTool.execute(mapOf("timezone" to "UTC"))
        assertTrue(result.success)
        assertTrue(result.displayText.contains("UTC"))
    }

    @Test
    fun testAssistantStatesCompleteness() {
        val expectedStates = setOf(
            "IDLE", "ACTIVATING", "LISTENING", "PROCESSING", "THINKING", "EXECUTING", "SPEAKING",
            "WAITING_FOR_CONFIRMATION", "SUCCESS", "ERROR"
        )
        val actualStates = AssistantStateType.entries.map { it.name }.toSet()
        assertEquals(expectedStates, actualStates)
    }

    @Test
    fun testNaturalLanguageUnderstandingWhatsApp() = runBlocking {
        val registry = ToolRegistry()
        val engine = DefaultAiEngine(registry)
        val res = engine.reason("WhatsApp kholo", AiContext())
        val isRecognized = when (res) {
            is AiReasoningResult.DirectResponse -> res.text.contains("WhatsApp", ignoreCase = true)
            is AiReasoningResult.ToolCall -> res.parameters["appName"]?.toString()?.contains("WhatsApp", ignoreCase = true) == true || (res.rationale?.contains("WhatsApp", ignoreCase = true) == true)
            is AiReasoningResult.RequiresConfirmation -> res.actionTitle.contains("WhatsApp", ignoreCase = true)
            else -> false
        }
        assertTrue("WhatsApp intent should be recognized", isRecognized)
    }

    @Test
    fun testNaturalLanguageUnderstandingCall() = runBlocking {
        val registry = ToolRegistry()
        val engine = DefaultAiEngine(registry)
        val res = engine.reason("SIM 1 se Ammi ko call karna", AiContext())
        val isRecognized = when (res) {
            is AiReasoningResult.DirectResponse -> res.text.contains("SIM 1", ignoreCase = true)
            is AiReasoningResult.ToolCall -> res.rationale?.contains("SIM 1", ignoreCase = true) == true
            is AiReasoningResult.RequiresConfirmation -> res.description.contains("SIM 1", ignoreCase = true) && res.actionTitle.contains("Ammi", ignoreCase = true)
            else -> false
        }
        assertTrue("Call with SIM 1 to Ammi should be recognized", isRecognized)
    }

    @Test
    fun testNaturalLanguageUnderstandingAlarm() = runBlocking {
        val registry = ToolRegistry()
        val engine = DefaultAiEngine(registry)
        val res = engine.reason("Kal subah 8 baje alarm laga do", AiContext())
        val isRecognized = when (res) {
            is AiReasoningResult.DirectResponse -> res.text.contains("Alarm", ignoreCase = true)
            is AiReasoningResult.ToolCall -> res.toolName.contains("alarm", ignoreCase = true)
            is AiReasoningResult.RequiresConfirmation -> res.actionTitle.contains("Alarm", ignoreCase = true)
            else -> false
        }
        assertTrue("Alarm scheduling intent should be recognized", isRecognized)
    }

    @Test
    fun testWakeActivationConfigDefaults() {
        val config = WakeActivationConfig()
        assertTrue(config.isVoiceActivationEnabled)
        assertEquals("Hey Astra", config.wakePhrase)
        assertTrue(config.isContinuousConversationEnabled)
    }
}
