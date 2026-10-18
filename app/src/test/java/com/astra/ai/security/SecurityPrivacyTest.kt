package com.astra.ai.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astra.ai.core.AstraConstants
import com.astra.ai.core.AstraLogger
import com.astra.ai.permissions.AstraPermission
import com.astra.ai.permissions.PermissionManager
import com.astra.ai.permissions.PermissionState
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
class SecurityPrivacyTest {

    private lateinit var context: Context
    private lateinit var permissionManager: PermissionManager
    private lateinit var confirmationManager: ConfirmationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        permissionManager = PermissionManager(context)
        confirmationManager = ConfirmationManager()
    }

    @Test
    fun testPermissionManager_doesNotRequestAllPermissionsAtLaunch() {
        val permissions = permissionManager.permissionsState.value
        assertNotNull(permissions)
        assertEquals(AstraPermission.entries.size, permissions.size)

        // All non-granted permissions must have a clear rationale
        permissions.filter { !it.isGranted }.forEach { status ->
            val rationale = permissionManager.getRationale(status.permission)
            assertNotNull(rationale)
            assertTrue(rationale.explanation.isNotBlank())
            assertTrue(rationale.title.isNotBlank())
            assertTrue(rationale.featureName.isNotBlank())
        }
    }

    @Test
    fun testPermissionManager_flowTransitions() {
        // Initial state for RECORD_AUDIO
        val initialState = permissionManager.checkPermission(AstraPermission.RECORD_AUDIO)
        assertTrue(
            "Initial state should be Granted or Denied",
            initialState is PermissionState.Denied || initialState is PermissionState.Granted
        )

        // User denies with rationale
        permissionManager.recordPermissionResult(
            permission = AstraPermission.RECORD_AUDIO,
            isGranted = false,
            shouldShowRationale = true
        )
        val stateAfterDenied = permissionManager.checkPermission(AstraPermission.RECORD_AUDIO)
        assertTrue("State should be Denied", stateAfterDenied is PermissionState.Denied)

        // User denies permanently (Don't ask again)
        permissionManager.recordPermissionResult(
            permission = AstraPermission.RECORD_AUDIO,
            isGranted = false,
            shouldShowRationale = false
        )
        val stateAfterPermanent = permissionManager.checkPermission(AstraPermission.RECORD_AUDIO)
        assertTrue("State should be PermanentlyDenied", stateAfterPermanent is PermissionState.PermanentlyDenied)

        // User grants permission
        permissionManager.recordPermissionResult(
            permission = AstraPermission.RECORD_AUDIO,
            isGranted = true
        )
        val stateAfterGrant = permissionManager.checkPermission(AstraPermission.RECORD_AUDIO)
        assertTrue("State should be Granted", stateAfterGrant is PermissionState.Granted)
    }

    @Test
    fun testConfirmationManager_identifiesConsequentialActions() {
        // Consequential external actions
        assertTrue(confirmationManager.isConsequentialAction("call_tool"))
        assertTrue(confirmationManager.isConsequentialAction("call_contact"))
        assertTrue(confirmationManager.isConsequentialAction("sms_tool"))
        assertTrue(confirmationManager.isConsequentialAction("send_message"))
        assertTrue(confirmationManager.isConsequentialAction("whatsapp_tool", mapOf("message" to "Salam")))
        assertTrue(confirmationManager.isConsequentialAction("delete_memory"))
        assertTrue(confirmationManager.isConsequentialAction("click_ui_element", mapOf("target" to "Post comment button")))

        // Safe / Read-only actions do NOT require confirmation
        assertFalse(confirmationManager.isConsequentialAction("world_clock"))
        assertFalse(confirmationManager.isConsequentialAction("device_status"))
        assertFalse(confirmationManager.isConsequentialAction("device_info"))
        assertFalse(confirmationManager.isConsequentialAction("quick_note", mapOf("note" to "test")))
    }

    @Test
    fun testConfirmationManager_buildsConsequentialPrompts() {
        // Instagram comment prompt
        val commentAction = confirmationManager.classifyAction("click_ui_element", mapOf("target" to "Post comment", "app" to "Instagram"))
        assertEquals(ConsequentialActionType.POST_COMMENT, commentAction)
        val (commentTitle, commentPrompt) = confirmationManager.buildConfirmationPrompt(
            ConsequentialActionType.POST_COMMENT,
            mapOf("app" to "Instagram", "comment" to "Nice photo!")
        )
        assertTrue(commentTitle.contains("Comment"))
        assertTrue(commentPrompt.contains("Instagram par ye comment post karun?"))

        // WhatsApp message prompt
        val (waTitle, waPrompt) = confirmationManager.buildConfirmationPrompt(
            ConsequentialActionType.SEND_WHATSAPP,
            mapOf("recipient" to "Ali", "message" to "Meeting at 5")
        )
        assertTrue(waPrompt.contains("WhatsApp par Ali ko ye message send karun?"))

        // Call prompt
        val (callTitle, callPrompt) = confirmationManager.buildConfirmationPrompt(
            ConsequentialActionType.PHONE_CALL,
            mapOf("contact" to "Ammi")
        )
        assertTrue(callPrompt.contains("Ammi ko call milana chahte hain"))
    }

    @Test
    fun testConfirmationManager_executionFlow() = runBlocking {
        var confirmed = false
        var cancelled = false

        val confirmation = ActionConfirmation(
            id = "test-action-1",
            actionType = ConsequentialActionType.POST_COMMENT,
            title = "Confirm Comment",
            prompt = "Instagram par ye comment post karun?",
            onConfirm = { confirmed = true },
            onCancel = { cancelled = true }
        )

        confirmationManager.requestConfirmation(confirmation)
        assertEquals("test-action-1", confirmationManager.pendingConfirmation.value?.id)

        // Confirm
        val executed = confirmationManager.confirm("test-action-1")
        assertTrue(executed)
        assertTrue(confirmed)
        assertFalse(cancelled)
        assertEquals(null, confirmationManager.pendingConfirmation.value)

        // Cancel test
        var cancelFlag = false
        val confirmation2 = ActionConfirmation(
            id = "test-action-2",
            actionType = ConsequentialActionType.PHONE_CALL,
            title = "Confirm Call",
            prompt = "Call Ammi?",
            onConfirm = {},
            onCancel = { cancelFlag = true }
        )
        confirmationManager.requestConfirmation(confirmation2)
        confirmationManager.cancel("test-action-2")
        assertTrue(cancelFlag)
        assertEquals(null, confirmationManager.pendingConfirmation.value)
    }

    @Test
    fun testAstraLogger_redactsSensitiveCredentials() {
        val messageWithGeminiKey = "Connecting with key AIzaSyD3x9183fakekey000111222333444 to model"
        val sanitizedGemini = AstraLogger.sanitize(messageWithGeminiKey)
        assertFalse("Should not contain plain Gemini key", sanitizedGemini.contains("AIzaSyD3x9183fakekey000111222333444"))
        assertTrue("Should contain redacted placeholder", sanitizedGemini.contains("***REDACTED"))

        val messageWithPassword = "Login request: password=secretPassword123"
        val sanitizedPassword = AstraLogger.sanitize(messageWithPassword)
        assertFalse("Should not contain plain password", sanitizedPassword.contains("secretPassword123"))

        val messageWithBearer = "Authorization header: Bearer ya29.a0AfH6SMfakeToken12345"
        val sanitizedBearer = AstraLogger.sanitize(messageWithBearer)
        assertFalse("Should not contain plain token", sanitizedBearer.contains("ya29.a0AfH6SMfakeToken12345"))
    }

    @Test
    fun testPrivacyPolicy_placeholderConfigured() {
        assertEquals("TODO_CONFIGURE_PRIVACY_POLICY_URL", AstraConstants.PRIVACY_POLICY_URL)
        assertEquals("https://api.astra.ai", AstraConstants.PRODUCTION_BACKEND_HTTPS_URL)
        assertTrue(AstraConstants.APP_VERSION.isNotBlank())
    }
}
