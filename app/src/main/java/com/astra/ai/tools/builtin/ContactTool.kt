package com.astra.ai.tools.builtin

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Contact representation retrieved from ContactsContract.
 */
data class DiscoveredContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val type: String = "Mobile"
)

/**
 * Tool: contact_lookup
 * Looks up contact information from Android ContactsContract.
 * Supports natural language queries like:
 * "Ammi ka number batao" -> contactName = "Ammi"
 */
class ContactTool(
    private val context: Context
) : Tool {

    override val name: String = "contact_lookup"

    override val description: String =
        "Looks up contacts and phone numbers from the device's ContactsContract by contact name or partial query."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "contactName",
            type = ToolParamType.STRING,
            description = "Name, relationship or nickname of the contact to find (e.g. Ammi, Abu, Ali, Mom)",
            required = true
        )
    )

    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.READ_CONTACTS
    )

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val startTime = System.currentTimeMillis()
        val queryName = (params["contactName"] as? String)?.trim()
        if (queryName.isNullOrBlank()) {
            return ToolExecutionResult.failure("Contact name is required to look up phone numbers.")
        }

        // Check READ_CONTACTS permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return ToolExecutionResult.failure(
                "READ_CONTACTS permission is required to search contacts. Please grant contact access in Settings."
            )
        }

        val contacts = searchContacts(context.contentResolver, queryName)
        val elapsed = System.currentTimeMillis() - startTime

        if (contacts.isEmpty()) {
            return ToolExecutionResult.failure(
                errorMessage = "No contact found matching \"$queryName\".",
                executionTimeMs = elapsed
            )
        }

        val primary = contacts.first()
        val displayText = if (contacts.size == 1) {
            "${primary.name}'s number is ${primary.phoneNumber}."
        } else {
            "Found ${contacts.size} contacts for \"$queryName\". Primary: ${primary.name} (${primary.phoneNumber})."
        }

        return ToolExecutionResult.success(
            displayText = displayText,
            output = mapOf(
                "query" to queryName,
                "foundCount" to contacts.size,
                "primaryName" to primary.name,
                "primaryNumber" to primary.phoneNumber,
                "contacts" to contacts.map { mapOf("name" to it.name, "number" to it.phoneNumber) }
            ),
            executionTimeMs = elapsed
        )
    }

    companion object {
        fun searchContacts(resolver: ContentResolver, query: String): List<DiscoveredContact> {
            val results = mutableListOf<DiscoveredContact>()
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )

            val cleanQuery = query.trim()
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$cleanQuery%")

            var cursor: Cursor? = null
            try {
                cursor = resolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                )

                cursor?.let { c ->
                    val idCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (c.moveToNext()) {
                        val id = if (idCol >= 0) c.getString(idCol) ?: "" else ""
                        val name = if (nameCol >= 0) c.getString(nameCol) ?: "" else ""
                        val number = if (numCol >= 0) c.getString(numCol) ?: "" else ""
                        if (name.isNotBlank() && number.isNotBlank()) {
                            results.add(DiscoveredContact(id, name, number))
                        }
                    }
                }
            } catch (e: Exception) {
                AstraLogger.e("ContactTool", "Failed querying contacts: ${e.message}")
            } finally {
                cursor?.close()
            }
            return results
        }
    }
}
