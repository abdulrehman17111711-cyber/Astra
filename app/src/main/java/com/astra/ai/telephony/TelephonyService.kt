package com.astra.ai.telephony

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.builtin.ContactMatcher
import com.astra.ai.tools.builtin.DiscoveredContact
import com.astra.ai.voice.VoicePhoneticCorrector

/**
 * Result model for contact resolution via Android ContactsContract.
 */
data class ResolvedContact(
    val displayName: String,
    val phoneNumber: String,
    val contactId: String? = null,
    val source: String = "ContactsContract"
)

/**
 * State of contact lookup.
 */
sealed class ContactResolutionResult {
    data class Found(val contact: ResolvedContact) : ContactResolutionResult()
    data class NotFound(val query: String, val explanation: String) : ContactResolutionResult()
    object PermissionRequired : ContactResolutionResult()
}

/**
 * Result of phone call initiation.
 */
data class CallExecutionResult(
    val success: Boolean,
    val displayText: String,
    val targetNumber: String? = null,
    val isDirectCall: Boolean = false,
    val simApplied: String = "Default SIM",
    val error: String? = null
)

/**
 * Production-grade Telephony Service for Astra AI.
 *
 * Uses Android's ContactsContract.CommonDataKinds.Phone.CONTENT_URI to perform robust
 * look-ups and resolve contact names to verified canonical phone numbers before attempting
 * any call intent, preventing random or fallback dialing.
 */
interface TelephonyService {
    fun resolveContact(contactNameOrQuery: String): ContactResolutionResult
    fun verifyContactExists(contactNameOrQuery: String): Boolean
    fun initiateCall(contactOrNumber: String, simPreference: String = "Default SIM"): CallExecutionResult
}

class AndroidTelephonyService(
    private val context: Context
) : TelephonyService {

    companion object {
        private const val TAG = "TelephonyService"
    }

    override fun verifyContactExists(contactNameOrQuery: String): Boolean {
        return resolveContact(contactNameOrQuery) is ContactResolutionResult.Found
    }

    override fun resolveContact(contactNameOrQuery: String): ContactResolutionResult {
        val raw = contactNameOrQuery.trim()
        if (raw.isBlank()) {
            return ContactResolutionResult.NotFound(raw, "Contact query is empty.")
        }

        // 1. Direct phone number check (e.g. "+923001234567", "03001234567", "0300-1234567")
        val digitsOnly = raw.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 3 && raw.all { it.isDigit() || it in "+- ()" }) {
            val canonicalNumber = formatToCanonicalNumber(digitsOnly)
            AstraLogger.i(TAG, "Target is direct phone number: $canonicalNumber")
            return ContactResolutionResult.Found(
                ResolvedContact(
                    displayName = raw,
                    phoneNumber = canonicalNumber,
                    source = "DirectNumber"
                )
            )
        }

        // 2. Check READ_CONTACTS permission
        val hasContactPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        // 3. Fetch custom contacts from AstraSettings as backup/override
        val customContacts = try {
            com.astra.ai.AstraApplication.instance.settingsRepository.settings.value.customContacts
        } catch (_: Exception) {
            emptyMap()
        }

        if (!hasContactPermission && customContacts.isEmpty()) {
            AstraLogger.w(TAG, "READ_CONTACTS permission missing and no custom contacts configured")
            return ContactResolutionResult.PermissionRequired
        }

        val resolver = context.contentResolver

        // 4. Phonetically normalize the query (e.g. "DMP Pariha" -> "DMP Fariha", "پریحہ" -> "فریحہ")
        val normalizedQuery = VoicePhoneticCorrector.normalizeForContactLookup(raw)
        AstraLogger.d(TAG, "Resolving contact for \"$raw\" (normalized: \"$normalizedQuery\")")

        // 5. Query ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val queryCandidates = mutableListOf(raw)
        if (normalizedQuery != raw) {
            queryCandidates.add(normalizedQuery)
        }
        val pCorrected = VoicePhoneticCorrector.swapUrduPeAndFe(raw)
        if (pCorrected != raw && !queryCandidates.contains(pCorrected)) {
            queryCandidates.add(pCorrected)
        }

        for (candidate in queryCandidates) {
            // First: Direct query via ContactsContract.CommonDataKinds.Phone.CONTENT_URI with canonical formatting
            val directMatches = queryPhoneContentUri(resolver, candidate)
            if (directMatches.isNotEmpty()) {
                val best = directMatches.first()
                val canonicalNum = formatToCanonicalNumber(best.phoneNumber)
                AstraLogger.i(TAG, "Resolved \"$raw\" via Phone.CONTENT_URI: ${best.name} ($canonicalNum)")
                return ContactResolutionResult.Found(
                    ResolvedContact(best.name, canonicalNum, best.id, "ContactsContract.Phone.CONTENT_URI")
                )
            }

            // Second: Multi-strategy phonetic & consonant skeleton matching
            val matches = ContactMatcher.findMatchingContacts(resolver, candidate, customContacts)
            if (matches.isNotEmpty()) {
                val best = matches.first()
                val canonicalNum = formatToCanonicalNumber(best.phoneNumber)
                AstraLogger.i(TAG, "Resolved \"$raw\" via ContactMatcher: ${best.name} ($canonicalNum)")
                return ContactResolutionResult.Found(
                    ResolvedContact(best.name, canonicalNum, best.id, "ContactMatcher")
                )
            }
        }

        // 6. Check custom address book directly
        for ((cName, cNum) in customContacts) {
            if (cName.equals(raw, ignoreCase = true) ||
                cName.contains(raw, ignoreCase = true) ||
                raw.contains(cName, ignoreCase = true) ||
                (normalizedQuery.isNotBlank() && cName.contains(normalizedQuery, ignoreCase = true))
            ) {
                val canonicalNum = formatToCanonicalNumber(cNum)
                AstraLogger.i(TAG, "Resolved \"$raw\" via customContacts: $cName ($canonicalNum)")
                return ContactResolutionResult.Found(
                    ResolvedContact(cName, canonicalNum, null, "CustomAddressBook")
                )
            }
        }

        // 7. Not found: Return structured failure explanation
        val reason = if (!hasContactPermission) {
            "Contacts permission is required to search \"$raw\" in your phonebook. Please grant Contacts permission in Settings."
        } else {
            "Could not find contact \"$raw\" in your phone contacts. Please verify the name or specify the phone number directly."
        }
        return ContactResolutionResult.NotFound(raw, reason)
    }

    override fun initiateCall(contactOrNumber: String, simPreference: String): CallExecutionResult {
        // STEP 1: Strict Verification & Canonical Lookup prior to attempting ANY Intent
        val resolution = resolveContact(contactOrNumber)

        val resolvedContact = when (resolution) {
            is ContactResolutionResult.Found -> resolution.contact
            is ContactResolutionResult.NotFound -> {
                AstraLogger.w(TAG, "Call aborted: contact verification failed: ${resolution.explanation}")
                return CallExecutionResult(
                    success = false,
                    displayText = resolution.explanation,
                    error = resolution.explanation
                )
            }
            is ContactResolutionResult.PermissionRequired -> {
                val err = "Contacts permission is required to find \"$contactOrNumber\" in your contacts. Please enable Contacts permission in Settings, or speak the phone number directly."
                AstraLogger.w(TAG, "Call aborted: permission required")
                return CallExecutionResult(
                    success = false,
                    displayText = err,
                    error = err
                )
            }
        }

        val phoneNumber = resolvedContact.phoneNumber
        if (phoneNumber.isBlank() || phoneNumber.length < 3) {
            val err = "A valid phone number was not found for \"${resolvedContact.displayName}\"."
            return CallExecutionResult(success = false, displayText = err, error = err)
        }

        // STEP 2: Check CALL_PHONE permission
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val simInfo = resolveSimAccount(simPreference)

        // STEP 3: Dispatch Intent with verified canonical number
        return try {
            val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val callIntent = Intent(action).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK

                simInfo.accountHandle?.let { handle ->
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                    putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", handle)
                }

                val slot = simInfo.slotIndex ?: if (simPreference.contains("2") || simPreference.contains("two", ignoreCase = true)) 1 else 0
                putExtra("com.android.phone.extra.slot", slot)
                putExtra("simSlot", slot)
                putExtra("slot", slot)
                putExtra("Subscription", slot)
            }

            context.startActivity(callIntent)

            val actionDesc = if (hasCallPermission) "Calling" else "Opened dialer for"
            val simDesc = if (simInfo.appliedSlot != null) " via ${simInfo.appliedSlot}" else ""
            val note = if (!hasCallPermission) " (Dialer opened because CALL_PHONE permission is not granted)" else ""

            CallExecutionResult(
                success = true,
                displayText = "$actionDesc ${resolvedContact.displayName} ($phoneNumber)$simDesc$note.",
                targetNumber = phoneNumber,
                isDirectCall = hasCallPermission,
                simApplied = simInfo.appliedSlot ?: simPreference
            )
        } catch (e: Exception) {
            AstraLogger.e(TAG, "Error initiating call: ${e.message}", e)
            CallExecutionResult(
                success = false,
                displayText = "Failed to place call to ${resolvedContact.displayName}: ${e.message}",
                error = e.message
            )
        }
    }

    /**
     * Performs a robust look-up using ContactsContract.CommonDataKinds.Phone.CONTENT_URI.
     * Evaluates exact display name matches, prefix matches, and sub-token containment.
     */
    private fun queryPhoneContentUri(resolver: ContentResolver, query: String): List<DiscoveredContact> {
        val results = mutableListOf<DiscoveredContact>()
        if (query.isBlank()) return results

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
        )

        // Multiple query selections: exact match first, prefix, and then wildcard LIKE
        val selections = listOf(
            Pair("${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?", arrayOf(query)),
            Pair("${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("$query%")),
            Pair("${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$query%"))
        )

        for ((selection, args) in selections) {
            var cursor: Cursor? = null
            try {
                cursor = resolver.query(
                    uri,
                    projection,
                    selection,
                    args,
                    "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )
                cursor?.let { c ->
                    val idCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val normCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)

                    while (c.moveToNext()) {
                        val id = if (idCol >= 0) c.getString(idCol) ?: "" else ""
                        val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                        val normalized = if (normCol >= 0) c.getString(normCol) ?: "" else ""
                        val rawNum = if (numCol >= 0) c.getString(numCol) ?: "" else ""
                        val chosenNumber = if (normalized.isNotBlank()) normalized else rawNum

                        if (name.isNotBlank() && chosenNumber.isNotBlank()) {
                            results.add(DiscoveredContact(id, name, chosenNumber))
                        }
                    }
                }
                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                AstraLogger.w(TAG, "Error in queryPhoneContentUri: ${e.message}")
            } finally {
                cursor?.close()
            }
        }
        return results
    }

    /**
     * Formats and strips formatting characters into a canonical telephone number string.
     */
    private fun formatToCanonicalNumber(number: String): String {
        val filtered = number.filter { it.isDigit() || it == '+' }
        return if (filtered.startsWith("00")) {
            "+" + filtered.substring(2)
        } else {
            filtered
        }
    }

    private data class SimResolution(
        val accountHandle: PhoneAccountHandle? = null,
        val slotIndex: Int? = null,
        val appliedSlot: String? = null
    )

    private fun resolveSimAccount(simPreference: String): SimResolution {
        val lower = simPreference.lowercase()
        val isSim2 = lower.contains("2") || lower.contains("two") || lower.contains("second")
        val isSim1 = lower.contains("1") || lower.contains("one") || lower.contains("first")
        val targetSlot = if (isSim2) 1 else 0

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                ?: return SimResolution(appliedSlot = if (isSim2) "SIM 2" else "SIM 1", slotIndex = targetSlot)

            val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPhoneStatePermission) {
                return SimResolution(appliedSlot = if (isSim2) "SIM 2" else "SIM 1", slotIndex = targetSlot)
            }

            val accounts = telecomManager.callCapablePhoneAccounts ?: emptyList()
            if (accounts.isNotEmpty() && targetSlot < accounts.size) {
                return SimResolution(
                    accountHandle = accounts[targetSlot],
                    slotIndex = targetSlot,
                    appliedSlot = if (targetSlot == 0) "SIM 1" else "SIM 2"
                )
            }
        } catch (_: Exception) {}

        return SimResolution(appliedSlot = if (isSim2) "SIM 2" else "SIM 1", slotIndex = targetSlot)
    }
}
