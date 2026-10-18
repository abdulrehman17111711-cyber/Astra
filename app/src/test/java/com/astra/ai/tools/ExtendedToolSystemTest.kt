package com.astra.ai.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astra.ai.ai.StructuredIntent
import com.astra.ai.ai.UniversalSemanticIntentEngine
import com.astra.ai.apps.AndroidAppManager
import com.astra.ai.security.ConfirmationManager
import com.astra.ai.settings.AstraSettings
import com.astra.ai.settings.DefaultSettingsRepository
import com.astra.ai.tools.builtin.AlarmTool
import com.astra.ai.tools.builtin.CallContactTool
import com.astra.ai.tools.builtin.CallTool
import com.astra.ai.tools.builtin.DeviceInfoTool
import com.astra.ai.tools.builtin.OpenAppTool
import com.astra.ai.tools.builtin.SearchAppTool
import com.astra.ai.tools.builtin.WhatsAppTool
import com.astra.ai.tools.builtin.YouTubeSearchParser
import com.astra.ai.tools.builtin.YouTubeTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExtendedToolSystemTest {

    private lateinit var context: Context
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var appManager: AndroidAppManager
    private lateinit var intentEngine: UniversalSemanticIntentEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        toolRegistry = ToolRegistry()
        appManager = AndroidAppManager(context)
        intentEngine = UniversalSemanticIntentEngine(toolRegistry, appManager)

        // Register tools
        toolRegistry.register(CallTool(context))
        toolRegistry.register(CallContactTool(context))
        toolRegistry.register(WhatsAppTool(context, appManager))
        toolRegistry.register(YouTubeTool(context, appManager))
        toolRegistry.register(SearchAppTool(context, appManager))
        toolRegistry.register(AlarmTool(context))
        toolRegistry.register(OpenAppTool(context, appManager))
        toolRegistry.register(DeviceInfoTool(context))
    }

    @Test
    fun testCallingIntentWithSim1Selection() {
        val query = "Ammi ko SIM 1 se call karo."
        val intent = intentEngine.parseIntent(query)

        assertEquals(StructuredIntent.ACTION_CALL, intent.action)
        assertEquals("Ammi", intent.parameters["contactName"])
        assertEquals("SIM 1", intent.parameters["sim"])
        assertTrue(intent.requiresConfirmation)
    }

    @Test
    fun testCallingDoesNotReportConnectedPrematurely() = runBlocking {
        val callTool = CallTool(context)
        // Calling a phone number
        val result = callTool.execute(mapOf(
            "contactOrNumber" to "+923001234567",
            "simPreference" to "SIM 1"
        ))

        assertTrue(result.success)
        // Must never claim call is connected merely because dialer/call intent was sent
        assertEquals(false, result.output["connected"])
        assertEquals("initiated", result.output["status"])
        assertTrue(result.displayText.contains("Initiated call") || result.displayText.contains("Opened dialer"))
    }

    @Test
    fun testWhatsAppMessagePreparationParsing() {
        val query = "WhatsApp par Ali ko message bhejo ke main 10 minute mein aa raha hoon."
        val intent = intentEngine.parseIntent(query)

        assertEquals(StructuredIntent.ACTION_MESSAGE, intent.action)
        assertEquals("WhatsApp", intent.targetApp)
        assertEquals("Ali", intent.parameters["contactName"])
        assertEquals("main 10 minute mein aa raha hoon.", intent.parameters["message"])
        assertTrue(intent.requiresConfirmation)
    }

    @Test
    fun testWhatsAppDoesNotReportMessageSentPrematurely() = runBlocking {
        val whatsAppTool = WhatsAppTool(context, appManager)
        val result = whatsAppTool.execute(mapOf(
            "recipient" to "Ali",
            "message" to "main 10 minute mein aa raha hoon."
        ))

        // WhatsApp may not be installed in Robolectric sandbox
        if (result.success) {
            assertEquals(false, result.output["sent"])
            assertEquals(true, result.output["prepared"])
            assertTrue(result.displayText.contains("Prepared WhatsApp message"))
        } else {
            assertTrue(result.displayText.contains("WhatsApp is not installed"))
        }
    }

    @Test
    fun testTikTokLatestVideoSearch() {
        val query = "TikTok par is account ki latest video dhoondo."
        val intent = intentEngine.parseIntent(query)

        assertEquals(StructuredIntent.ACTION_SEARCH, intent.action)
        assertEquals("TikTok", intent.targetApp)
        assertTrue(intent.parameters["query"].toString().isNotBlank())
        assertFalse(intent.parameters["query"].toString().contains("dhoondo"))
    }

    @Test
    fun testTikTokAccountSearchWithAtSymbol() = runBlocking {
        val searchTool = SearchAppTool(context, appManager)
        val result = searchTool.execute(mapOf(
            "application" to "TikTok",
            "query" to "@khaby.lame"
        ))

        assertTrue(result.success)
        assertEquals(false, result.output["commentPosted"])
        assertTrue(result.displayText.contains("TikTok"))
    }

    @Test
    fun testInstagramAccountSearch() = runBlocking {
        val searchTool = SearchAppTool(context, appManager)
        val result = searchTool.execute(mapOf(
            "application" to "Instagram",
            "query" to "@natgeo"
        ))

        assertTrue(result.success)
        assertEquals(false, result.output["commentPosted"])
        assertTrue(result.displayText.contains("Instagram"))
    }

    @Test
    fun testAllFiveExactYouTubeQueriesProduceCID() {
        val testQueries = listOf(
            "YouTube pe search karo CID",
            "YouTube par CID search kar do",
            "Search YouTube for CID",
            "CID YouTube par dhoondo",
            "Hey Astra, YouTube khol kar CID search karo"
        )

        for (query in testQueries) {
            val parserExtracted = YouTubeSearchParser.extractQuery(query)
            assertEquals("YouTubeSearchParser should extract 'CID' for '$query'", "CID", parserExtracted)

            val intent = intentEngine.parseIntent(query)
            assertEquals("Intent action should be SEARCH for '$query'", StructuredIntent.ACTION_SEARCH, intent.action)
            assertEquals("Intent targetApp should be YouTube for '$query'", "YouTube", intent.targetApp)
            assertEquals("Intent query should be 'CID' for '$query'", "CID", intent.parameters["query"])
        }
    }

    @Test
    fun testAlarmExtractionMorningSeven() {
        val query = "Kal subah 7 baje alarm laga do."
        val intent = intentEngine.parseIntent(query)

        assertEquals(StructuredIntent.ACTION_ALARM, intent.action)
        assertEquals(7, intent.parameters["hour"])
        assertEquals(0, intent.parameters["minute"])
        assertEquals("AM", intent.parameters["amPm"])
    }

    @Test
    fun testTimerExecutionFiveMinutes() = runBlocking {
        val query = "5 minute ka timer laga do."
        val intent = intentEngine.parseIntent(query)

        assertEquals(StructuredIntent.ACTION_ALARM, intent.action)
        assertEquals(300, intent.parameters["durationSeconds"])

        val alarmTool = AlarmTool(context)
        val result = alarmTool.execute(mapOf("durationSeconds" to 300, "message" to "Tea Timer"))
        assertTrue(result.success)
        assertEquals(300, result.output["durationSeconds"])
        assertEquals("timer", result.output["type"])
    }

    @Test
    fun testCheckIfAppIsInstalledIntent() {
        val query = "Check karo mere phone mein TikTok installed hai ya nahi."
        val intent = intentEngine.parseIntent(query)

        assertEquals("TikTok", intent.targetApp)
        assertEquals(true, intent.parameters["checkOnly"])
        assertEquals("open_app", intent.mappedTool)
    }

    @Test
    fun testOpenAppCheckOnlyExecution() = runBlocking {
        val openAppTool = OpenAppTool(context, appManager)
        val result = openAppTool.execute(mapOf(
            "appName" to "NonExistentApp12345",
            "checkOnly" to true
        ))

        assertTrue(result.success)
        assertEquals(false, result.output["isInstalled"])
        assertTrue(result.displayText.contains("is not installed"))
    }

    @Test
    fun testBatteryAndDeviceTelemetry() = runBlocking {
        val query = "Battery kitni hai?"
        val intent = intentEngine.parseIntent(query)

        assertEquals(StructuredIntent.ACTION_DEVICE_STATUS, intent.action)
        assertEquals("device_info_tool", intent.mappedTool)

        val deviceInfoTool = DeviceInfoTool(context)
        val result = deviceInfoTool.execute(mapOf("queryType" to "battery"))
        assertTrue(result.success)
        assertNotNull(result.output["batteryLevel"])
    }

    @Test
    fun testTrustedAutomationSettingBypassesRoutineConfirmation() {
        val confirmationManager = ConfirmationManager()
        val settingsRepo = DefaultSettingsRepository()

        val callParams = mapOf("contactOrNumber" to "Ammi", "simPreference" to "SIM 1")

        // 1. By default, calling is consequential and requires confirmation
        val defaultSettings = settingsRepo.settings.value
        assertTrue(confirmationManager.isConfirmationRequired("call_tool", callParams, defaultSettings))

        // 2. Enable Trusted Automation and remember trust for Ammi
        settingsRepo.setTrustedAutomation(true)
        val trustKey = confirmationManager.buildActionTrustKey("call_tool", callParams)
        settingsRepo.addTrustedAction(trustKey)

        val updatedSettings = settingsRepo.settings.value
        assertFalse(
            "Trusted contact call should not require repeated confirmation when trusted automation is enabled",
            confirmationManager.isConfirmationRequired("call_tool", callParams, updatedSettings)
        )

        // 3. Sensitive / financial action MUST still require confirmation regardless of setting
        assertTrue(
            "Purchase/financial action must always require confirmation",
            confirmationManager.isConfirmationRequired("purchase_tool", mapOf("amount" to 500), updatedSettings)
        )
    }
}
