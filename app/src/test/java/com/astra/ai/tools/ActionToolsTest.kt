package com.astra.ai.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astra.ai.apps.AppDiscoveryManager
import com.astra.ai.tools.builtin.AlarmTool
import com.astra.ai.tools.builtin.AppLaunchTool
import com.astra.ai.tools.builtin.BrowserTool
import com.astra.ai.tools.builtin.CallTool
import com.astra.ai.tools.builtin.CameraTool
import com.astra.ai.tools.builtin.ContactTool
import com.astra.ai.tools.builtin.DeviceInfoTool
import com.astra.ai.tools.builtin.GalleryTool
import com.astra.ai.tools.builtin.NotificationTool
import com.astra.ai.tools.builtin.SettingsTool
import com.astra.ai.tools.builtin.SmsTool
import com.astra.ai.tools.builtin.WhatsAppTool
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
@Config(sdk = [34])
class ActionToolsTest {

    private lateinit var context: Context
    private lateinit var appManager: com.astra.ai.apps.AppManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appManager = com.astra.ai.apps.AndroidAppManager(context)
    }

    @Test
    fun `test AppLaunchTool handles uninstalled application cleanly`() = runBlocking {
        val tool = AppLaunchTool(context, appManager)
        val result = tool.execute(mapOf("appName" to "UnknownAppXYZ"))

        assertFalse("Uninstalled app should return success=false", result.success)
        assertTrue(result.displayText.contains("is not installed"))
        assertTrue("Tool verification should pass on well-formed failure", tool.verify(result))
    }

    @Test
    fun `test ContactTool without permission explains requirement`() = runBlocking {
        val tool = ContactTool(context)
        val result = tool.execute(mapOf("contactName" to "Ammi"))

        assertFalse(result.success)
        assertTrue(result.displayText.contains("READ_CONTACTS") || result.displayText.contains("permission"))
        assertTrue(tool.verify(result))
    }

    @Test
    fun `test CallTool requires confirmation and resolves phone intent`() = runBlocking {
        val tool = CallTool(context)
        assertTrue("Call tool must require confirmation", tool.requiresConfirmation)

        val result = tool.execute(
            mapOf("contactOrNumber" to "+923001234567", "simPreference" to "SIM 1")
        )
        assertNotNull(result)
        assertTrue(tool.verify(result))
    }

    @Test
    fun `test SmsTool requires confirmation and prepares SMS intent`() = runBlocking {
        val tool = SmsTool(context)
        assertTrue("Sms tool must require confirmation", tool.requiresConfirmation)

        val result = tool.execute(
            mapOf("recipient" to "Ali", "message" to "Main 10 minute mein aa raha hoon")
        )
        assertNotNull(result)
        assertTrue(result.success)
        assertTrue(result.displayText.contains("Ali"))
        assertTrue(tool.verify(result))
    }

    @Test
    fun `test AlarmTool creates alarm result`() = runBlocking {
        val tool = AlarmTool(context)
        val result = tool.execute(
            mapOf("hour" to 7, "minute" to 30, "amPm" to "AM", "message" to "Morning Alarm")
        )

        assertNotNull(result)
        assertTrue(result.success)
        assertEquals(7, result.output["hour"])
        assertEquals(30, result.output["minute"])
        assertTrue(tool.verify(result))
    }

    @Test
    fun `test BrowserTool handles URL and search query`() = runBlocking {
        val tool = BrowserTool(context)

        // Web search query
        val searchResult = tool.execute(mapOf("queryOrUrl" to "Android 16 release date"))
        assertTrue(searchResult.success)
        assertTrue(searchResult.displayText.contains("Searching web"))

        // Direct URL
        val urlResult = tool.execute(mapOf("queryOrUrl" to "https://android.com"))
        assertTrue(urlResult.success)
        assertTrue(urlResult.displayText.contains("https://android.com"))
        assertTrue(tool.verify(urlResult))
    }

    @Test
    fun `test YouTubeTool handles search query`() = runBlocking {
        val tool = YouTubeTool(context, appManager)
        val result = tool.execute(mapOf("query" to "Android 16"))

        assertNotNull(result)
        assertTrue(result.success)
        assertTrue(result.displayText.contains("YouTube"))
        assertTrue(tool.verify(result))
    }

    @Test
    fun `test WhatsAppTool requires confirmation and detects installation status`() = runBlocking {
        val tool = WhatsAppTool(context, appManager)
        assertTrue(tool.requiresConfirmation)

        val result = tool.execute(
            mapOf("recipient" to "Ali", "message" to "Assalam o alaikum")
        )
        assertNotNull(result)
        // In Robolectric test runner WhatsApp is not installed
        assertFalse(result.success)
        assertTrue(result.displayText.contains("WhatsApp is not installed"))
        assertTrue(tool.verify(result))
    }

    @Test
    fun `test CameraTool executes without crashing`() = runBlocking {
        val tool = CameraTool(context)
        val result = tool.execute(mapOf("mode" to "photo"))
        assertNotNull(result)
        assertTrue(tool.verify(result))
    }

    @Test
    fun `test GalleryTool executes without crashing`() = runBlocking {
        val tool = GalleryTool(context)
        val result = tool.execute(mapOf("mediaType" to "images"))
        assertNotNull(result)
        assertTrue(tool.verify(result))
    }

    @Test
    fun `test SettingsTool opens wifi and general settings`() = runBlocking {
        val tool = SettingsTool(context)
        val wifiResult = tool.execute(mapOf("section" to "wifi"))
        assertTrue(wifiResult.success)
        assertTrue(wifiResult.displayText.contains("Wifi Settings"))

        val bluetoothResult = tool.execute(mapOf("section" to "bluetooth"))
        assertTrue(bluetoothResult.success)
        assertTrue(bluetoothResult.displayText.contains("Bluetooth Settings"))
    }

    @Test
    fun `test DeviceInfoTool queries telemetry`() = runBlocking {
        val tool = DeviceInfoTool(context)
        val result = tool.execute(mapOf("queryType" to "all"))

        assertTrue(result.success)
        assertTrue(result.output.containsKey("batteryLevel"))
        assertTrue(result.output.containsKey("deviceModel"))
        assertTrue(result.output.containsKey("androidVersion"))
        assertTrue(tool.verify(result))
    }

    @Test
    fun `test NotificationTool validates empty message`() = runBlocking {
        val tool = NotificationTool(context)
        val result = tool.execute(mapOf("title" to "Notice", "message" to ""))

        assertFalse(result.success)
        assertTrue(result.displayText.contains("cannot be empty"))
        assertTrue(tool.verify(result))
    }
}
