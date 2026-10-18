package com.astra.ai.ai

import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolArgumentValidator
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter
import com.astra.ai.tools.ToolRegistry
import com.astra.ai.tools.ValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Validates structured AI tool calling with validated arguments and
 * natural multilingual instruction parsing without brittle command templates.
 */
class StructuredAiToolCallingTest {

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var intentEngine: UniversalSemanticIntentEngine
    private lateinit var aiEngine: DefaultAiEngine

    @Before
    fun setup() {
        toolRegistry = ToolRegistry()

        // Register dummy test tools
        toolRegistry.register(object : Tool {
            override val name: String = "youtube_search"
            override val description: String = "Searches YouTube"
            override val parameters: List<ToolParameter> = listOf(
                ToolParameter("query", ToolParamType.STRING, "Search query", required = true)
            )
            override suspend fun execute(params: Map<String, Any?>) = ToolExecutionResult.success("Done")
        })

        toolRegistry.register(object : Tool {
            override val name: String = "search_app"
            override val description: String = "Searches inside an app"
            override val parameters: List<ToolParameter> = listOf(
                ToolParameter("application", ToolParamType.STRING, "Target application", required = true),
                ToolParameter("query", ToolParamType.STRING, "Search query", required = true)
            )
            override suspend fun execute(params: Map<String, Any?>) = ToolExecutionResult.success("Done")
        })

        toolRegistry.register(object : Tool {
            override val name: String = "open_app"
            override val description: String = "Opens an app"
            override val parameters: List<ToolParameter> = listOf(
                ToolParameter("appName", ToolParamType.STRING, "App name", required = false),
                ToolParameter("packageName", ToolParamType.STRING, "Package name", required = false)
            )
            override suspend fun execute(params: Map<String, Any?>) = ToolExecutionResult.success("Done")
        })

        toolRegistry.register(object : Tool {
            override val name: String = "call_contact"
            override val description: String = "Calls a contact"
            override val requiresConfirmation: Boolean = true
            override val parameters: List<ToolParameter> = listOf(
                ToolParameter("contactName", ToolParamType.STRING, "Contact name", required = true),
                ToolParameter("sim", ToolParamType.STRING, "SIM card", required = false, defaultValue = "Default SIM")
            )
            override suspend fun execute(params: Map<String, Any?>) = ToolExecutionResult.success("Done")
        })

        toolRegistry.register(object : Tool {
            override val name: String = "alarm_tool"
            override val description: String = "Sets an alarm"
            override val parameters: List<ToolParameter> = listOf(
                ToolParameter("hour", ToolParamType.INT, "Hour (0-23)", required = true),
                ToolParameter("minute", ToolParamType.INT, "Minute (0-59)", required = false, defaultValue = 0),
                ToolParameter("amPm", ToolParamType.STRING, "AM or PM", required = false, defaultValue = "AM"),
                ToolParameter("message", ToolParamType.STRING, "Label", required = false, defaultValue = "Astra Alarm")
            )
            override suspend fun execute(params: Map<String, Any?>) = ToolExecutionResult.success("Done")
        })

        toolRegistry.register(object : Tool {
            override val name: String = "whatsapp_message"
            override val description: String = "Sends WhatsApp message"
            override val requiresConfirmation: Boolean = true
            override val parameters: List<ToolParameter> = listOf(
                ToolParameter("contactName", ToolParamType.STRING, "Contact name", required = true),
                ToolParameter("message", ToolParamType.STRING, "Message body", required = true)
            )
            override suspend fun execute(params: Map<String, Any?>) = ToolExecutionResult.success("Done")
        })

        toolRegistry.register(object : Tool {
            override val name: String = "quick_note"
            override val description: String = "Saves note"
            override val parameters: List<ToolParameter> = listOf(
                ToolParameter("content", ToolParamType.STRING, "Note content", required = true),
                ToolParameter("category", ToolParamType.STRING, "Category", required = false, defaultValue = "personal")
            )
            override suspend fun execute(params: Map<String, Any?>) = ToolExecutionResult.success("Done")
        })

        intentEngine = UniversalSemanticIntentEngine(toolRegistry, null)
        aiEngine = DefaultAiEngine(toolRegistry, null, null)
    }

    @Test
    fun testYouTubePeSearchKaroCIDExtractsCleanCID() {
        val intent = intentEngine.parseIntent("YouTube pe search karo CID")
        assertEquals(StructuredIntent.ACTION_SEARCH, intent.action)
        assertEquals("YouTube", intent.targetApp)
        val extractedQuery = intent.parameters["query"] as? String
        assertEquals("CID", extractedQuery)
        assertFalse("Query should not contain 'karo'", extractedQuery?.contains("karo", ignoreCase = true) == true)
        assertFalse("Query should not contain 'search'", extractedQuery?.contains("search", ignoreCase = true) == true)
        assertFalse("Query should not contain 'pe'", extractedQuery?.contains("pe", ignoreCase = true) == true)
    }

    @Test
    fun testTikTokPeSearchKaroCookingRecipes() {
        val intent = intentEngine.parseIntent("TikTok pe search karo cooking recipes")
        assertEquals(StructuredIntent.ACTION_SEARCH, intent.action)
        assertEquals("TikTok", intent.targetApp)
        val query = intent.parameters["query"] as? String
        assertEquals("cooking recipes", query)
        assertFalse("Query should not contain 'karo'", query?.contains("karo", ignoreCase = true) == true)
        assertEquals("search_app", intent.mappedTool)
    }

    @Test
    fun testInstagramPeSearchKaroTechNews() {
        val intent = intentEngine.parseIntent("Instagram pe search karo tech news")
        assertEquals(StructuredIntent.ACTION_SEARCH, intent.action)
        assertEquals("Instagram", intent.targetApp)
        val query = intent.parameters["query"] as? String
        assertEquals("tech news", query)
        assertEquals("search_app", intent.mappedTool)
    }

    @Test
    fun testCallingWithConfirmation() {
        val intent = intentEngine.parseIntent("Ammi ko call karo")
        assertEquals(StructuredIntent.ACTION_CALL, intent.action)
        assertEquals("Phone", intent.targetApp)
        assertTrue("Calls must require confirmation", intent.requiresConfirmation)
        val contact = intent.parameters["contactName"] as? String
        assertEquals("Ammi", contact)
    }

    @Test
    fun testWhatsAppMessagingWithConfirmation() {
        val intent = intentEngine.parseIntent("WhatsApp pe Ali ko bolo main pohanch gaya")
        assertEquals(StructuredIntent.ACTION_MESSAGE, intent.action)
        assertEquals("WhatsApp", intent.targetApp)
        assertTrue("Messages must require confirmation", intent.requiresConfirmation)
        val contact = intent.parameters["contactName"] as? String
        assertEquals("Ali", contact)
        val msg = intent.parameters["message"] as? String
        assertEquals("main pohanch gaya", msg)
    }

    @Test
    fun testAlarmExtraction() {
        val intent = intentEngine.parseIntent("Subah 7 baje ka alarm lagao")
        assertEquals(StructuredIntent.ACTION_ALARM, intent.action)
        assertEquals("Clock", intent.targetApp)
        assertEquals(7, intent.parameters["hour"])
        assertEquals("AM", intent.parameters["amPm"])
    }

    @Test
    fun testMemoryExtraction() {
        val intent = intentEngine.parseIntent("Remember that my car keys are on the kitchen table")
        assertEquals(StructuredIntent.ACTION_NOTE, intent.action)
        assertEquals("Memory", intent.targetApp)
        val content = intent.parameters["content"] as? String
        assertEquals("my car keys are on the kitchen table", content)
    }

    @Test
    fun testToolArgumentValidatorTypeCoercionAndBounds() {
        val alarmTool = toolRegistry.getTool("alarm_tool")!!

        // Valid with String hour "7" coerced to Int 7
        val validResult = ToolArgumentValidator.validate(alarmTool, mapOf("hour" to "7", "minute" to "30"))
        assertTrue(validResult is ValidationResult.Valid)
        val sanitized = (validResult as ValidationResult.Valid).sanitizedParams
        assertEquals(7, sanitized["hour"])
        assertEquals(30, sanitized["minute"])

        // Out-of-bounds hour 28
        val invalidResult = ToolArgumentValidator.validate(alarmTool, mapOf("hour" to 28))
        assertTrue(invalidResult is ValidationResult.Invalid)

        // Missing required parameter
        val missingResult = ToolArgumentValidator.validate(alarmTool, emptyMap())
        assertTrue(missingResult is ValidationResult.Invalid)
    }

    @Test
    fun testAiEngineProducesExecutableToolCalls() = runBlocking {
        val result = aiEngine.reason("YouTube pe search karo CID", AiContext())
        assertTrue("Result should be ToolCall", result is AiReasoningResult.ToolCall)
        val toolCall = result as AiReasoningResult.ToolCall
        assertEquals("youtube_search", toolCall.toolName)
        assertEquals("CID", toolCall.parameters["query"])
    }
}
