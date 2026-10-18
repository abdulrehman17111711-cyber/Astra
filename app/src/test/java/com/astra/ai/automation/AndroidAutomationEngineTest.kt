package com.astra.ai.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astra.ai.ai.StructuredIntent
import com.astra.ai.apps.AndroidAppManager
import com.astra.ai.apps.AppCapability
import com.astra.ai.apps.AppDiscoveryManager
import com.astra.ai.apps.AppLaunchResult
import com.astra.ai.apps.AppManager
import com.astra.ai.apps.InstalledApp
import com.astra.ai.apps.LaunchMethod
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
@Config(sdk = [36])
class AndroidAutomationEngineTest {

    private lateinit var context: Context
    private lateinit var fakeAppManager: FakeAppManager
    private lateinit var automationEngine: AndroidAutomationEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakeAppManager = FakeAppManager()
        automationEngine = AndroidAutomationEngine(context, fakeAppManager)
    }

    @Test
    fun testAppDiscoveryIdentifiesDisplayNamePackageAndLaunchMethod() {
        val app = InstalledApp(
            appName = "Calculator",
            packageName = "com.android.calculator2",
            launchActivity = "com.android.calculator2.Calculator",
            launchMethod = LaunchMethod.MAIN_LAUNCHER_ACTIVITY,
            isEnabled = true,
            isLaunchable = true,
            capabilities = setOf(AppCapability.LAUNCHABLE)
        )

        assertEquals("Calculator", app.appName)
        assertEquals("com.android.calculator2", app.packageName)
        assertEquals(LaunchMethod.MAIN_LAUNCHER_ACTIVITY, app.launchMethod)
        assertTrue(app.isLaunchable)
        assertTrue(app.matchesQuery("Calculator"))
        assertTrue(app.matchesQuery("calculator"))
        assertTrue(app.matchesQuery("com.android.calculator2"))
    }

    @Test
    fun testOpenInstalledAppSuccess() = runBlocking {
        fakeAppManager.apps.add(
            InstalledApp(
                appName = "Settings",
                packageName = "com.android.settings",
                launchActivity = "com.android.settings.Settings",
                launchMethod = LaunchMethod.MAIN_LAUNCHER_ACTIVITY
            )
        )

        val result = automationEngine.openApp("Settings")
        assertTrue("Opening installed app should succeed", result.isSuccess)
        val success = result as AutomationStepResult.Success
        assertTrue(success.message.contains("Opening Settings"))
    }

    @Test
    fun testOpenMissingAppReportsActualFailureWithoutFaking() = runBlocking {
        val result = automationEngine.openApp("NonExistentApp123")
        assertFalse("Opening non-existent app must report failure", result.isSuccess)
        val failure = result as AutomationStepResult.Failed
        assertTrue(failure.reason.contains("not installed"))
        assertFalse("Must not claim success for missing app", failure.isSuccess)
    }

    @Test
    fun testAccessibilityDisabledReportsClearOutcome() = runBlocking {
        val result = automationEngine.tapElement("Settings Button")
        assertFalse(result.isSuccess)
        val failure = result as AutomationStepResult.Failed
        assertTrue(failure.isUnsupported)
        assertTrue(failure.reason.contains("Accessibility service is disabled"))
    }

    @Test
    fun testMultiStepPlanExecutionStopsOnNonOptionalFailure() = runBlocking {
        val actions = listOf(
            AutomationAction.LaunchApp("MissingAppA"),
            AutomationAction.TapElement("Next"),
            AutomationAction.EnterText("Test Query")
        )

        val report = automationEngine.executePlan("Multi-step task", actions)
        assertFalse("Plan should report failure because step 1 failed", report.isSuccess)
        assertEquals(1, report.executedSteps.size)
        assertTrue(report.summary.contains("Could not complete"))
        assertTrue(report.summary.contains("not installed"))
    }

    @Test
    fun testAutomationPlanGeneratorFromSearchIntent() {
        val intent = StructuredIntent(
            action = StructuredIntent.ACTION_SEARCH,
            targetApp = "YouTube",
            goal = "Search YouTube for CID",
            parameters = mapOf("query" to "CID")
        )

        val actions = AutomationPlanGenerator.planFromIntent(intent)
        assertEquals(3, actions.size)
        assertTrue(actions[0] is AutomationAction.LaunchApp)
        assertEquals("YouTube", (actions[0] as AutomationAction.LaunchApp).appNameOrPackage)
        assertTrue(actions[1] is AutomationAction.TapElement)
        assertEquals("search", (actions[1] as AutomationAction.TapElement).targetQuery)
        assertTrue(actions[2] is AutomationAction.EnterText)
        assertEquals("CID", (actions[2] as AutomationAction.EnterText).text)
    }

    @Test
    fun testOpenBlankAppNameReturnsFailure() = runBlocking {
        val result = automationEngine.openApp("   ")
        assertFalse(result.isSuccess)
        val failure = result as AutomationStepResult.Failed
        assertTrue(failure.reason.contains("must not be empty"))
    }

    @Test
    fun testAccessibilityDisabledReportsClearOutcomeForEnterText() = runBlocking {
        val result = automationEngine.enterText("Hello world", "search_box")
        assertFalse(result.isSuccess)
        val failure = result as AutomationStepResult.Failed
        assertTrue(failure.isUnsupported)
        assertTrue(failure.reason.contains("Accessibility service is disabled"))
    }

    @Test
    fun testAccessibilityDisabledReportsClearOutcomeForScroll() = runBlocking {
        val result = automationEngine.scroll(forward = true)
        assertFalse(result.isSuccess)
        val failure = result as AutomationStepResult.Failed
        assertTrue(failure.isUnsupported)
        assertTrue(failure.reason.contains("Accessibility service is disabled"))
    }

    @Test
    fun testAccessibilityDisabledReportsClearOutcomeForNavigateBack() = runBlocking {
        val result = automationEngine.navigateBack(1)
        assertFalse(result.isSuccess)
        val failure = result as AutomationStepResult.Failed
        assertTrue(failure.isUnsupported)
        assertTrue(failure.reason.contains("Accessibility service is disabled"))
    }

    @Test
    fun testAccessibilityDisabledReportsClearOutcomeForWaitForElement() = runBlocking {
        val result = automationEngine.waitForElement("Submit", timeoutMs = 500L)
        assertFalse(result.isSuccess)
        val failure = result as AutomationStepResult.Failed
        assertTrue(failure.isUnsupported)
        assertTrue(failure.reason.contains("Accessibility service is disabled"))
    }

    @Test
    fun testScreenInspectionWhenAccessibilityDisabledReturnsClearError() {
        val observation = automationEngine.inspectScreen()
        assertNotNull(observation.error)
        assertTrue(observation.error!!.contains("Astra Accessibility Service is not enabled"))
    }

    @Test
    fun testMultiStepPlanContinuesWhenFailingStepIsOptional() = runBlocking {
        fakeAppManager.apps.add(
            InstalledApp(
                appName = "Settings",
                packageName = "com.android.settings",
                launchActivity = "com.android.settings.Settings",
                launchMethod = LaunchMethod.MAIN_LAUNCHER_ACTIVITY
            )
        )

        // Step 1: Open app (succeeds)
        // Step 2: Optional tap on missing banner (fails but optional, so continues)
        // Step 3: Inspect screen (succeeds)
        val actions = listOf(
            AutomationAction.LaunchApp("Settings"),
            AutomationAction.TapElement("NonExistentOptionalBanner", optional = true),
            AutomationAction.InspectScreen
        )

        val report = automationEngine.executePlan("Open settings with optional banner dismissal", actions)
        assertTrue("Plan should succeed because failed step was optional", report.isSuccess)
        assertEquals(3, report.executedSteps.size)
        assertTrue(report.executedSteps[0].isSuccess)
        assertFalse(report.executedSteps[1].isSuccess)
        assertTrue(report.executedSteps[2].isSuccess)
        assertTrue(report.summary.contains("successfully"))
    }

    @Test
    fun testMultiStepPlanAllSuccessProducesComprehensiveReport() = runBlocking {
        fakeAppManager.apps.add(
            InstalledApp(
                appName = "YouTube",
                packageName = "com.google.android.youtube",
                launchActivity = "com.google.android.youtube.HomeActivity",
                launchMethod = LaunchMethod.MAIN_LAUNCHER_ACTIVITY
            )
        )

        val actions = listOf(
            AutomationAction.LaunchApp("YouTube"),
            AutomationAction.InspectScreen
        )

        val report = automationEngine.executePlan("Launch and inspect YouTube", actions)
        assertTrue(report.isSuccess)
        assertEquals(2, report.totalSteps)
        assertEquals(2, report.executedSteps.size)
        assertTrue(report.summary.contains("2/2 steps"))
        assertNotNull(report.finalObservation)
    }

    @Test
    fun testAutomationStepResultFailedCapturesMissingElementAndReason() {
        val action = AutomationAction.TapElement("Search Button")
        val failed = AutomationStepResult.Failed(
            action = action,
            reason = "Could not find element on screen",
            missingElement = "Search Button",
            isUnsupported = false,
            executionTimeMs = 120L
        )

        assertFalse(failed.isSuccess)
        assertEquals("Search Button", failed.missingElement)
        assertEquals("Could not find element on screen", failed.reason)
        assertEquals(120L, failed.executionTimeMs)
    }

    @Test
    fun testAutomationPlanGeneratorFromLaunchIntent() {
        val intent = StructuredIntent(
            action = StructuredIntent.ACTION_LAUNCH,
            targetApp = "Calculator",
            goal = "Open Calculator"
        )
        val actions = AutomationPlanGenerator.planFromIntent(intent)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is AutomationAction.LaunchApp)
        assertEquals("Calculator", (actions[0] as AutomationAction.LaunchApp).appNameOrPackage)
    }

    @Test
    fun testAutomationPlanGeneratorFromMessageIntent() {
        val intent = StructuredIntent(
            action = StructuredIntent.ACTION_MESSAGE,
            targetApp = "WhatsApp",
            goal = "Send WhatsApp message to Ali",
            parameters = mapOf("recipient" to "Ali", "message" to "Meeting at 5")
        )
        val actions = AutomationPlanGenerator.planFromIntent(intent)
        assertEquals(5, actions.size)
        assertTrue(actions[0] is AutomationAction.LaunchApp)
        assertEquals("WhatsApp", (actions[0] as AutomationAction.LaunchApp).appNameOrPackage)
        assertTrue(actions[1] is AutomationAction.TapElement)
        assertEquals("search", (actions[1] as AutomationAction.TapElement).targetQuery)
        assertTrue(actions[2] is AutomationAction.EnterText)
        assertEquals("Ali", (actions[2] as AutomationAction.EnterText).text)
        assertTrue(actions[3] is AutomationAction.TapElement)
        assertEquals("Ali", (actions[3] as AutomationAction.TapElement).targetQuery)
        assertTrue(actions[4] is AutomationAction.EnterText)
        assertEquals("Meeting at 5", (actions[4] as AutomationAction.EnterText).text)
    }

    /**
     * Fake AppManager for isolated JVM testing.
     */
    private class FakeAppManager : AppManager {
        val apps = mutableListOf<InstalledApp>()

        override fun getInstalledApps(): List<InstalledApp> = apps

        override fun findAppByName(name: String): InstalledApp? {
            return apps.firstOrNull { it.matchesQuery(name) }
        }

        override fun launchApp(packageName: String): Boolean {
            return apps.any { it.packageName == packageName }
        }

        override fun launchAppDetailed(nameOrPackage: String): AppLaunchResult {
            val app = findAppByName(nameOrPackage)
                ?: return AppLaunchResult.NotInstalled(nameOrPackage, "\"$nameOrPackage\" is not installed.")
            return AppLaunchResult.Success(app, "Opening ${app.appName}...")
        }

        override fun refreshInstalledApps(onComplete: ((List<InstalledApp>) -> Unit)?) {
            onComplete?.invoke(apps)
        }

        override val discoveryManager: AppDiscoveryManager
            get() = throw UnsupportedOperationException()
    }
}
