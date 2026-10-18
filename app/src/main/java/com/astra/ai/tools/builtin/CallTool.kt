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
    private val context: Context
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

        // 1. Resolve phone number: if target already looks like numbers, use directly; otherwise lookup via ContactsContract
        val resolvedNumber = resolvePhoneNumber(target)
        if (resolvedNumber.isBlank()) {
            return ToolExecutionResult.failure("Could not find a phone number for \"$target\". Please verify contact permissions or specify a direct number.")
        }

        // 2. Check CALL_PHONE permission
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        // 3. Telecom SIM slot account resolution
        val simInfo = resolveSimAccount(requestedSim)

        return try {
            val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val callIntent = Intent(action).apply {
                data = Uri.parse("tel:$resolvedNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK

                // Attach SIM preference to Intent extras using official Telecom extra keys
                simInfo.accountHandle?.let { handle ->
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
            }

            context.startActivity(callIntent)
            val elapsed = System.currentTimeMillis() - startTime

            val actionDescription = if (hasCallPermission) "Initiated call to" else "Opened dialer for"
            val simDescription = if (simInfo.appliedSlot != null) " via ${simInfo.appliedSlot}" else ""
            val note = if (!hasCallPermission) " (Dialer opened because CALL_PHONE permission is not granted)" else ""
            val limitNote = if (simInfo.limitationReason != null) " (${simInfo.limitationReason})" else ""

            ToolExecutionResult.success(
                displayText = "$actionDescription $target ($resolvedNumber)$simDescription$note$limitNote. Call initiated; call connection depends on carrier and recipient.",
                output = mapOf(
                    "target" to target,
                    "phoneNumber" to resolvedNumber,
                    "simApplied" to (simInfo.appliedSlot ?: requestedSim),
                    "directCall" to hasCallPermission,
                    "status" to "initiated",
                    "connected" to false
                ),
                executionTimeMs = elapsed
            )
        } catch (e: Exception) {
            AstraLogger.e("CallTool", "Execution error: ${e.message}")
            ToolExecutionResult.failure("Failed to initiate call to $target: ${e.message}")
        }
    }

    private fun resolvePhoneNumber(target: String): String {
        val digitsOnly = target.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 3) {
            return digitsOnly
        }

        // Query contacts
        val hasContactPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasContactPermission) {
            val contacts = ContactTool.searchContacts(context.contentResolver, target)
            if (contacts.isNotEmpty()) {
                return contacts.first().phoneNumber.filter { it.isDigit() || it == '+' }
            }
        }
        return ""
    }

    private data class SimResolution(
        val accountHandle: PhoneAccountHandle? = null,
        val appliedSlot: String? = null,
        val limitationReason: String? = null
    )

    private fun resolveSimAccount(simPreference: String): SimResolution {
        if (simPreference.equals("Default SIM", ignoreCase = true)) {
            return SimResolution(appliedSlot = "Default SIM")
        }

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                ?: return SimResolution(limitationReason = "Telecom service unavailable")

            val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPhoneStatePermission) {
                return SimResolution(
                    limitationReason = "READ_PHONE_STATE permission needed for hardware SIM selection. Falling back to default SIM."
                )
            }

            val callCapableAccounts: List<PhoneAccountHandle> = telecomManager.callCapablePhoneAccounts ?: emptyList()
            if (callCapableAccounts.isEmpty()) {
                return SimResolution(limitationReason = "No SIM accounts reported by TelecomManager.")
            }

            val slotIndex = when {
                simPreference.contains("1") -> 0
                simPreference.contains("2") -> 1
                else -> 0
            }

            return if (slotIndex < callCapableAccounts.size) {
                SimResolution(
                    accountHandle = callCapableAccounts[slotIndex],
                    appliedSlot = if (slotIndex == 0) "SIM 1" else "SIM 2"
                )
            } else {
                SimResolution(
                    accountHandle = callCapableAccounts.firstOrNull(),
                    appliedSlot = "SIM 1",
                    limitationReason = "Requested SIM slot $slotIndex not found. Falling back to primary SIM."
                )
            }
        } catch (e: SecurityException) {
            return SimResolution(limitationReason = "Permission restricted: ${e.message}")
        } catch (e: Exception) {
            return SimResolution(limitationReason = "Telecom error: ${e.message}")
        }
    }
}
