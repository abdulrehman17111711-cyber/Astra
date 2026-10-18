package com.astra.ai.accessibility

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.astra.ai.apps.AppDiscoveryManager
import com.astra.ai.tools.builtin.AccessibilityTool
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityAgentTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AccessibilityState.updateConnected(null, false)
    }

    @Test
    fun testUiNodeInfo_interactiveAndDisplayLabel() {
        val buttonNode = UiNodeInfo(
            text = "Search",
            contentDescription = "Search button",
            resourceId = "com.test:id/btn_search",
            isClickable = true,
            isEnabled = true
        )

        assertTrue(buttonNode.isInteractive)
        assertEquals("Search", buttonNode.displayLabel)

        val nonInteractive = UiNodeInfo(
            text = "Title Text",
            isClickable = false,
            isEditable = false,
            isScrollable = false
        )
        assertFalse(nonInteractive.isInteractive)
    }

    @Test
    fun testUiObservation_summaryAndSecurityFlag() {
        val rootNode = UiNodeInfo(
            text = "Home Feed",
            isClickable = false,
            children = listOf(
                UiNodeInfo(
                    text = "Explore",
                    contentDescription = "Explore tab",
                    isClickable = true
                ),
                UiNodeInfo(
                    text = "Search",
                    contentDescription = "Magnifying glass",
                    isClickable = true
                )
            )
        )

        val observation = UiObservation(
            packageName = "com.instagram.android",
            rootNodes = listOf(rootNode)
        )

        assertEquals(3, observation.allNodes().size)
        assertEquals(2, observation.findInteractiveNodes().size)
        val summary = observation.summary()
        assertTrue(summary.contains("com.instagram.android"))
        assertTrue(summary.contains("Explore") || summary.contains("Search"))
    }

    @Test
    fun testUiNodeFinder_semanticTermExpansion() {
        val searchExpanded = UiNodeFinder.expandSemanticTerms("search")
        assertTrue(searchExpanded.contains("magnifying"))
        assertTrue(searchExpanded.contains("find"))
        assertTrue(searchExpanded.contains("explore"))
        assertTrue(searchExpanded.contains("search button"))

        val sendExpanded = UiNodeFinder.expandSemanticTerms("send")
        assertTrue(sendExpanded.contains("submit"))
        assertTrue(sendExpanded.contains("enter"))

        val backExpanded = UiNodeFinder.expandSemanticTerms("back")
        assertTrue(backExpanded.contains("close"))
        assertTrue(backExpanded.contains("cancel"))
    }

    @Test
    fun testUiNodeFinder_textNormalization() {
        assertEquals("instagram search", UiNodeFinder.normalize("  Instagram: Search!  "))
        assertEquals("whatsapp messenger", UiNodeFinder.normalize("WhatsApp Messenger..."))
        assertEquals("ali khan", UiNodeFinder.normalize("Ali   Khan"))
    }

    @Test
    fun testMatchPriority_ordering() {
        // Priority 1 (RESOURCE_ID) must be ordered before Priority 2 (CONTENT_DESCRIPTION)
        assertTrue(MatchPriority.RESOURCE_ID.tier < MatchPriority.CONTENT_DESCRIPTION.tier)
        assertTrue(MatchPriority.CONTENT_DESCRIPTION.tier < MatchPriority.EXACT_TEXT.tier)
        assertTrue(MatchPriority.EXACT_TEXT.tier < MatchPriority.NORMALIZED_TEXT.tier)
        assertTrue(MatchPriority.NORMALIZED_TEXT.tier < MatchPriority.PARTIAL_TEXT.tier)
        assertTrue(MatchPriority.PARTIAL_TEXT.tier < MatchPriority.CLASS_TYPE.tier)
        assertTrue(MatchPriority.CLASS_TYPE.tier < MatchPriority.CONTEXTUAL.tier)
        assertTrue(MatchPriority.CONTEXTUAL.tier < MatchPriority.COORDINATES.tier)
    }

    @Test
    fun testAccessibilityState_lifecycleAndFlow() {
        val initial = AccessibilityState.state.value
        assertFalse(initial.isServiceConnected)

        AccessibilityState.updateConnected(null, true)
        assertTrue(AccessibilityState.state.value.isServiceConnected)

        val obs = UiObservation(
            packageName = "com.spotify.music",
            rootNodes = listOf(UiNodeInfo(text = "Play"))
        )
        AccessibilityState.updateObservation(obs)
        assertEquals("com.spotify.music", AccessibilityState.state.value.activePackage)
        assertEquals(1, AccessibilityState.state.value.nodeCount)

        AccessibilityState.updateLastAction("Clicked: Play")
        assertEquals("Clicked: Play", AccessibilityState.state.value.lastAction)

        AccessibilityState.updateConnected(null, false)
        assertFalse(AccessibilityState.state.value.isServiceConnected)
    }

    @Test
    fun testUiActionExecutor_serviceDisabledGracefulMessage() = runBlocking {
        AccessibilityState.updateConnected(null, false)

        val clickResult = UiActionExecutor.click("search")
        assertFalse(clickResult.success)
        assertTrue(clickResult.message.contains("Astra Accessibility Service is not enabled"))

        val typeResult = UiActionExecutor.typeText("hello", "search")
        assertFalse(typeResult.success)
        assertTrue(typeResult.message.contains("Astra Accessibility Service is not enabled"))

        val scrollResult = UiActionExecutor.scrollForward()
        assertFalse(scrollResult.success)
        assertTrue(scrollResult.message.contains("Astra Accessibility Service is not enabled"))
    }

    @Test
    fun testAccessibilityTool_executionWhenDisabled() = runBlocking {
        val appDiscoveryManager = AppDiscoveryManager(context)
        val appManager = com.astra.ai.apps.AndroidAppManager(context, appDiscoveryManager)
        val tool = AccessibilityTool(context, appManager)

        assertEquals("accessibility_action", tool.name)
        assertTrue(tool.parameters.isNotEmpty())

        val result = tool.execute(mapOf("action" to "click", "target" to "search"))
        assertFalse(result.success)
        assertTrue(result.displayText.contains("Astra Accessibility Service is not enabled"))
    }
}
