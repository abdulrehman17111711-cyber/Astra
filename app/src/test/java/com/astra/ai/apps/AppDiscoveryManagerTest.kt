package com.astra.ai.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.astra.ai.tools.builtin.OpenAppTool
import com.astra.ai.tools.builtin.RefreshAppsTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDiscoveryManagerTest {

    private lateinit var context: Context
    private lateinit var appDiscoveryManager: AppDiscoveryManager
    private lateinit var appManager: AndroidAppManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Setup fake installed launchable applications in Robolectric ShadowPackageManager
        val pm = context.packageManager
        val shadowPm = shadowOf(pm)

        // Add Spotify
        val spotifyResolveInfo = createResolveInfo("com.spotify.music", "Spotify", "com.spotify.music.MainActivity")
        val spotifyIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        shadowPm.addResolveInfoForIntent(spotifyIntent, spotifyResolveInfo)
        shadowPm.installPackage(createPackageInfo("com.spotify.music", "Spotify", "8.9.18", 12345L))

        // Add WhatsApp
        val whatsappResolveInfo = createResolveInfo("com.whatsapp", "WhatsApp", "com.whatsapp.Main")
        shadowPm.addResolveInfoForIntent(spotifyIntent, whatsappResolveInfo)
        shadowPm.installPackage(createPackageInfo("com.whatsapp", "WhatsApp", "2.24.5", 54321L))

        // Add disabled app
        val disabledResolveInfo = createResolveInfo("com.example.disabled", "Disabled Tool", "com.example.disabled.MainActivity")
        shadowPm.addResolveInfoForIntent(spotifyIntent, disabledResolveInfo)
        shadowPm.installPackage(createPackageInfo("com.example.disabled", "Disabled Tool", "1.0", 1L, enabled = false))

        appDiscoveryManager = AppDiscoveryManager(context)
        appManager = AndroidAppManager(context, appDiscoveryManager)
    }

    private fun createResolveInfo(packageName: String, label: String, activityName: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                this.name = activityName
                this.applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    this.name = label
                    this.enabled = true
                }
            }
        }
    }

    private fun createPackageInfo(
        packageName: String,
        appName: String,
        version: String,
        code: Long,
        enabled: Boolean = true
    ): PackageInfo {
        return PackageInfo().apply {
            this.packageName = packageName
            this.versionName = version
            this.applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                this.enabled = enabled
            }
        }
    }

    @Test
    fun `test normalization handles spaces, punctuation and aliases`() {
        assertEquals("whatsapp", InstalledApp.normalize("what's app"))
        assertEquals("whatsapp messenger", InstalledApp.normalize("WhatsApp Messenger"))
        assertEquals("spotify", InstalledApp.normalize("  Spotify  "))
        assertEquals("google chrome", InstalledApp.normalize("Google-Chrome"))
    }

    @Test
    fun `test app matching matches exact, partial, and aliases`() {
        val app = InstalledApp(
            appName = "WhatsApp",
            packageName = "com.whatsapp",
            aliases = listOf("what's app", "whatsapp messenger")
        )

        assertTrue("Exact name should match", app.matchesQuery("WhatsApp"))
        assertTrue("Lowercase name should match", app.matchesQuery("whatsapp"))
        assertTrue("Alias 'what\\'s app' should match", app.matchesQuery("what's app"))
        assertTrue("Package substring should match", app.matchesQuery("com.whatsapp"))
        assertFalse("Unrelated app should not match", app.matchesQuery("Instagram"))
    }

    @Test
    fun `test app lookup by name and alias`() {
        appDiscoveryManager.refreshInstalledApps()
        val foundSpotify = appDiscoveryManager.findApp("Spotify")
        assertNotNull("Should find Spotify", foundSpotify)
        assertEquals("com.spotify.music", foundSpotify?.packageName)

        val foundByLower = appDiscoveryManager.findApp("spotify")
        assertNotNull("Should find spotify case-insensitively", foundByLower)

        val notFound = appDiscoveryManager.findApp("NonExistentApp123")
        assertNull("Should return null for non-installed app", notFound)
    }

    @Test
    fun `test launchApp with non-installed application reports not installed without faking success`() {
        val result = appDiscoveryManager.launchApp("UnknownApp999")
        assertTrue("Result should be NotInstalled", result is AppLaunchResult.NotInstalled)
        val notInstalled = result as AppLaunchResult.NotInstalled
        assertEquals("\"UnknownApp999\" is not installed.", notInstalled.message)
    }

    @Test
    fun `test OpenAppTool execution for installed and not installed apps`() = runBlocking {
        val tool = OpenAppTool(context, appManager)

        // Test non-installed app
        val failResult = tool.execute(mapOf("appName" to "Cyberpunk2077"))
        assertFalse("Tool should fail for non-installed app", failResult.success)
        assertTrue("Should explicitly state not installed", (failResult.error ?: "").contains("not installed"))

        // Test missing query
        val emptyResult = tool.execute(emptyMap())
        assertFalse("Tool should fail when no target specified", emptyResult.success)
    }

    @Test
    fun `test RefreshAppsTool refreshes registry`() = runBlocking {
        val refreshTool = RefreshAppsTool(appManager)
        val result = refreshTool.execute(mapOf("query" to "Spotify"))

        assertTrue("Refresh should succeed", result.success)
        assertNotNull(result.output["totalApps"])
    }

    @Test
    fun `test ResultVerifier passes for OpenAppTool failure on non-installed app`() = runBlocking {
        val tool = OpenAppTool(context, appManager)
        val result = tool.execute(mapOf("appName" to "NonExistentAppXYZ"))
        assertFalse(result.success)
        assertEquals("NonExistentAppXYZ is not installed.", result.displayText)

        val verifier = com.astra.ai.assistant.ResultVerifier()
        val verification = verifier.verify(tool, result)
        assertTrue("Verification should pass for legitimate failure message", verification is com.astra.ai.assistant.VerificationResult.Passed)
    }

    @Test
    fun `test dynamic refresh on package added broadcast`() {
        // Broadcast package added
        val intent = Intent(Intent.ACTION_PACKAGE_ADDED).apply {
            data = Uri.parse("package:com.new.app")
        }
        context.sendBroadcast(intent)

        // Verify receiver catches without crash and installedApps state flow is accessible
        assertNotNull(appDiscoveryManager.installedApps.value)
    }
}
