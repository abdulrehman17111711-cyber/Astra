package com.astra.ai.tools.builtin

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.astra.ai.tools.Tool
import com.astra.ai.tools.ToolExecutionResult
import com.astra.ai.tools.ToolParamType
import com.astra.ai.tools.ToolParameter

/**
 * Tool: call_contact
 * Places a phone call to a contact with SIM preference.
 * Requires user confirmation before execution.
 */
class CallContactTool(
    private val context: Context
) : Tool {

    override val name: String = "call_contact"

    override val description: String =
        "Initiates a phone call to a specified contact or phone number with optional SIM preference."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "contactName",
            type = ToolParamType.STRING,
            description = "Name or relationship of the contact (e.g. Ammi, Mom, John)",
            required = true
        ),
        ToolParameter(
            name = "simPreference",
            type = ToolParamType.STRING,
            description = "SIM slot preference to place call on (e.g. SIM 1, SIM 2, or Default SIM)",
            required = false
        )
    )

    override val requiresConfirmation: Boolean = true

    override suspend fun execute(params: Map<String, Any?>): ToolExecutionResult {
        val contact = (params["contactName"] ?: params["contactOrNumber"] ?: params["contact"] ?: params["recipient"] ?: "").toString().trim()
        if (contact.isBlank()) {
            return ToolExecutionResult.failure("Contact name or phone number is required.")
        }
        val delegate = CallTool(context)
        val callParams = mutableMapOf<String, Any?>("contactOrNumber" to contact)
        params["simPreference"]?.let { callParams["simPreference"] = it }
        params["sim"]?.let { callParams["simPreference"] = it }
        return delegate.execute(callParams)
    }
}
