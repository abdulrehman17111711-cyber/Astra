package com.astra.ai.tools.builtin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.astra.ai.core.AstraLogger
import com.astra.ai.telephony.AndroidTelephonyService
import com.astra.ai.telephony.TelephonyService
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: call_tool
 * Initiates phone calls using official Android TelecomManager / Intent APIs.
 * Supports:
 * - Contact name lookup (via ContactsContract)
 * - Direct dial / call
 * - ACTION_CALL when CALL_PHONE permission is granted, otherwise graceful ACTION_DIAL fallback
 * - Real SIM detection via TelecomManager phone accounts
 * - Consequential action: requires confirmation before execution
 */
class CallTool(
    private val context: Context,
    private val telephonyService: TelephonyService = AndroidTelephonyService(context)
) : Tool {

    override val name: String = "call_tool"

    override val description: String =
        "Places or dials a phone call to a contact or direct number with SIM selection (SIM 1, SIM 2, or Default SIM)."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "contactOrNumber",
            type = ToolParamType.STRING,
            description = "Contact name (e.g. Ammi, Abu, John) or phone number (e.g. +923001234567)",
            required = true
        ),
        ToolParameter(
            name = "simPreference",
            type = ToolParamType.STRING,
            description = "SIM slot preference: 'SIM 1', 'SIM 2', or 'Default SIM'",
            required = false
        )
    )

    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.READ_CONTACTS
    )

    override val requiresConfirmation: Boolean = true

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val target = (params["contactOrNumber"] ?: params["contactName"] ?: params["phoneNumber"] ?: params["contact"] ?: params["number"] ?: params["recipient"])?.toString()?.trim()

        if (target.isNullOrBlank()) {
            return ToolExecutionResult.failure("Please specify a contact name or phone number to call.")
        }

        val requestedSim = (params["simPreference"] ?: params["sim"])?.toString()?.trim()?.ifEmpty { "Default SIM" } ?: "Default SIM"

        val callResult = telephonyService.initiateCall(target, requestedSim)
        val elapsed = System.currentTimeMillis() - startTime

        return if (callResult.success) {
            ToolExecutionResult.success(
                displayText = callResult.displayText,
                output = mapOf(
                    "target" to target,
                    "phoneNumber" to (callResult.targetNumber ?: ""),
                    "simApplied" to callResult.simApplied,
                    "directCall" to callResult.isDirectCall,
                    "status" to "initiated",
                    "connected" to false
                ),
                executionTimeMs = elapsed
            )
        } else {
            ToolExecutionResult.failure(callResult.displayText)
        }
    }

    fun resolvePhoneNumber(target: String): String {
        val digitsOnly = target.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 3 && target.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) {
            return digitsOnly
        }

        // Query contacts using intelligent multilingual ContactMatcher
        val hasContactPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val customContacts = try {
            com.astra.ai.AstraApplication.instance.settingsRepository.settings.value.customContacts
        } catch (_: Exception) {
            emptyMap()
        }

        if (hasContactPermission || customContacts.isNotEmpty()) {
            val contacts = ContactMatcher.findMatchingContacts(context.contentResolver, target, customContacts)
            if (contacts.isNotEmpty()) {
                return contacts.first().phoneNumber.filter { it.isDigit() || it == '+' }
            }
        }
        return ""
    }

    private data class SimResolution(
        val accountHandle: PhoneAccountHandle? = null,
        val slotIndex: Int? = null,
        val appliedSlot: String? = null,
        val limitationReason: String? = null
    )

    private fun resolveSimAccount(simPreference: String): SimResolution {
        val lower = simPreference.lowercase()
        val isSim2 = lower.contains("2") || lower.contains("ٹو") || lower.contains("two") || lower.contains("دوسری")
        val isSim1 = lower.contains("1") || lower.contains("ون") || lower.contains("one") || lower.contains("پہلی")
        val targetSlot = if (isSim2) 1 else 0

        if (simPreference.equals("Default SIM", ignoreCase = true) && !isSim1 && !isSim2) {
            return SimResolution(appliedSlot = "Default SIM", slotIndex = 0)
        }

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                ?: return SimResolution(appliedSlot = if (isSim2) "SIM 2" else "SIM 1", slotIndex = targetSlot, limitationReason = "Telecom service unavailable")

            val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPhoneStatePermission) {
                return SimResolution(
                    appliedSlot = if (isSim2) "SIM 2" else "SIM 1",
                    slotIndex = targetSlot,
                    limitationReason = "Using hardware slot index $targetSlot (grant READ_PHONE_STATE for account binding)."
                )
            }

            val callCapableAccounts: List<PhoneAccountHandle> = telecomManager.callCapablePhoneAccounts ?: emptyList()
            if (callCapableAccounts.isEmpty()) {
                return SimResolution(
                    appliedSlot = if (isSim2) "SIM 2" else "SIM 1",
                    slotIndex = targetSlot,
                    limitationReason = "Direct OEM slot $targetSlot applied."
                )
            }

            return if (targetSlot < callCapableAccounts.size) {
                SimResolution(
                    accountHandle = callCapableAccounts[targetSlot],
                    slotIndex = targetSlot,
                    appliedSlot = if (targetSlot == 0) "SIM 1" else "SIM 2"
                )
            } else {
                SimResolution(
                    accountHandle = callCapableAccounts.firstOrNull(),
                    slotIndex = 0,
                    appliedSlot = "SIM 1",
                    limitationReason = "Requested SIM slot $targetSlot not found. Using primary SIM."
                )
            }
        } catch (e: SecurityException) {
            return SimResolution(appliedSlot = if (isSim2) "SIM 2" else "SIM 1", slotIndex = targetSlot, limitationReason = "Permission restricted: ${e.message}")
        } catch (e: Exception) {
            return SimResolution(appliedSlot = if (isSim2) "SIM 2" else "SIM 1", slotIndex = targetSlot, limitationReason = "Telecom error: ${e.message}")
        }
    }
}
