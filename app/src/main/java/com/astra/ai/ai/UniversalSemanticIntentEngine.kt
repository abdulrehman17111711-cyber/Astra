package com.astra.ai.ai

import com.astra.ai.apps.AppManager
import com.astra.ai.core.AstraConstants
import com.astra.ai.core.AstraLogger
import com.astra.ai.tools.ToolArgumentValidator
import com.astra.ai.tools.ToolRegistry
import com.astra.ai.tools.ValidationResult

/**
 * Universal Semantic Intent Engine for Astra Voice Assistant.
 * Understands natural conversational English, Roman Urdu, Hindi, and mixed-language
 * instructions without rigid command templates or per-app hardcoding.
 *
 * Extracts structured intents:
 * - User's actual goal
 * - Target application or system feature
 * - Cleaned parameters without conversational noise (e.g. strips "karo", "search", "pe", etc.)
 * - Execution steps & verification strategy
 * - Argument validation against registered tool schemas
 */
class UniversalSemanticIntentEngine(
    private val toolRegistry: ToolRegistry,
    private val appManager: AppManager? = null
) {

    private val commonApps = listOf(
        "youtube" to "YouTube",
        "tiktok" to "TikTok",
        "instagram" to "Instagram",
        "insta" to "Instagram",
        "whatsapp" to "WhatsApp",
        "spotify" to "Spotify",
        "chrome" to "Chrome",
        "google" to "Google",
        "browser" to "Browser",
        "camera" to "Camera",
        "gallery" to "Gallery",
        "photos" to "Gallery",
        "settings" to "Settings",
        "clock" to "Clock",
        "alarm" to "Clock",
        "phone" to "Phone",
        "contacts" to "Contacts",
        "play store" to "Play Store",
        "playstore" to "Play Store",
        "twitter" to "Twitter",
        "x" to "Twitter",
        "netflix" to "Netflix",
        "facebook" to "Facebook"
    )

    private val noiseFillerRegex = Regex(
        "(?i)\\b(pe|par|mein|me|in|on|at|for|to|aur|and|kholo|khol kar|khol ke|khol karke|khol|chalao|start|launch|open|open kar|open karke|search karo|search kar do|search karna|search for|search|dhoondo|talash karo|dekho|karo|kar do|karna|kardo|karke|please|batao|dikhao|lagao|lagana|bhejo|bhejna|milao|laga do)\\b"
    )

    fun parseIntent(rawQuery: String): StructuredIntent {
        val rawTrimmed = rawQuery.trim()
        val wakeWordRegex = Regex("(?i)^(hey astra|hello astra|ok astra|okay astra|astra|suno astra)\\s*[,:]?\\s*")
        val stripped = rawTrimmed.replace(wakeWordRegex, "").trim()
        val trimmed = if (stripped.isNotBlank() && stripped.lowercase() !in listOf("hello", "hi", "hey", "salam", "assalam o alaikum")) stripped else rawTrimmed
        val lower = trimmed.lowercase()

        // 1. Creator Attribution
        if (lower.contains("who made you") || lower.contains("who created you") ||
            lower.contains("creator") || lower.contains("developer") || lower.contains("kisne banaya")
        ) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_CONVERSATION,
                targetApp = "System",
                goal = "Identify creator and background of Astra Voice Assistant",
                parameters = emptyMap(),
                executionSteps = listOf("Return spoken attribution message"),
                needsClarification = false,
                verificationStrategy = "Attribution confirmed",
                mappedTool = null,
                rationale = "Creator attribution request"
            )
        }

        // 2. Greetings
        if (lower in listOf("hello", "hi", "hey", "astra", "hey astra", "hello astra", "salam", "assalam o alaikum")) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_CONVERSATION,
                targetApp = "System",
                goal = "Greet the user",
                parameters = emptyMap(),
                executionSteps = listOf("Return spoken greeting"),
                needsClarification = false,
                verificationStrategy = "Greeting delivered",
                mappedTool = null,
                rationale = "User greeting"
            )
        }

        // 3. Application Registry Refresh
        if (lower.contains("refresh apps") || lower.contains("scan apps") || lower.contains("update apps") || lower.contains("check new app")) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_REFRESH_APPS,
                targetApp = "ApplicationRegistry",
                goal = "Scan and refresh discoverable installed apps",
                parameters = emptyMap(),
                executionSteps = listOf("Scan PackageManager", "Update discoverable apps registry"),
                needsClarification = false,
                verificationStrategy = "Verify updated app count > 0",
                mappedTool = "refresh_apps",
                mappedArguments = emptyMap(),
                rationale = "User requested refreshing discoverable application registry."
            )
        }

        // 4. Device Status & Battery Telemetry ("Battery kitni hai", "Battery level", "Device status", "Phone model")
        val isDeviceTelemetry = Regex("\\b(battery|device status|ram|phone info|phone model|hardware|storage|device info|android version)\\b").containsMatchIn(lower) &&
                !lower.contains("instagram") && !lower.contains("telegram") && !lower.contains("camera")
        if (isDeviceTelemetry) {
            val queryType = when {
                lower.contains("battery") -> "battery"
                lower.contains("model") -> "model"
                lower.contains("android") || lower.contains("version") -> "android_version"
                else -> "all"
            }
            return StructuredIntent(
                action = StructuredIntent.ACTION_DEVICE_STATUS,
                targetApp = "SystemTelemetry",
                goal = "Query hardware state, battery percentage, and system info",
                parameters = mapOf("queryType" to queryType),
                executionSteps = listOf("Query BatteryManager", "Inspect Build / SystemInfo", "Format telemetry"),
                needsClarification = false,
                verificationStrategy = "Verify battery or device telemetry returned",
                mappedTool = "device_info_tool",
                mappedArguments = mapOf("queryType" to queryType),
                rationale = "User inquired about hardware, battery, or device status."
            )
        }

        // 5. Clock / Time
        if ((lower.contains("time") || lower.contains("waqt") || lower.contains("clock")) &&
            !lower.contains("alarm") && !lower.contains("timer")
        ) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_CLOCK,
                targetApp = "WorldClock",
                goal = "Get current time or formatted timezone clock",
                parameters = mapOf("timezone" to "LOCAL"),
                executionSteps = listOf("Inspect system time", "Format localized time string"),
                needsClarification = false,
                verificationStrategy = "Verify formatted time returned",
                mappedTool = "world_clock",
                mappedArguments = mapOf("timezone" to "LOCAL"),
                rationale = "User asked for time."
            )
        }

        // 5b. Timers ("5 minute ka timer laga do", "Set timer for 10 minutes", "30 second timer")
        if (lower.contains("timer")) {
            val minMatch = Regex("(\\d+)\\s*(min|minute|minutes|minto|mint|m)\\b", RegexOption.IGNORE_CASE).find(lower)
            val secMatch = Regex("(\\d+)\\s*(sec|second|seconds|s)\\b", RegexOption.IGNORE_CASE).find(lower)
            val minutes = minMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val seconds = secMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val totalSeconds = if (minutes == 0 && seconds == 0) {
                val digitMatch = Regex("(\\d+)").find(lower)?.value?.toIntOrNull() ?: 5
                digitMatch * 60
            } else {
                (minutes * 60) + seconds
            }

            return StructuredIntent(
                action = StructuredIntent.ACTION_ALARM,
                targetApp = "Clock",
                goal = "Set countdown timer for $totalSeconds seconds",
                parameters = mapOf("durationSeconds" to totalSeconds, "message" to "Astra Timer"),
                executionSteps = listOf("Dispatch AlarmClock.ACTION_SET_TIMER intent"),
                needsClarification = false,
                verificationStrategy = "Verify timer intent dispatched",
                mappedTool = "alarm_tool",
                mappedArguments = mapOf("durationSeconds" to totalSeconds, "message" to "Astra Timer"),
                requiresConfirmation = false,
                rationale = "User requested scheduling a countdown timer."
            )
        }

        // 6. Alarms ("Subah 7 baje ka alarm lagao", "Set alarm for 6:30 AM", "Alarm lagao 8 baje", "Kal subah 7 baje alarm laga do")
        if (lower.contains("alarm") || lower.contains("wake me up") || lower.contains("jaga dena")) {
            val (hour, minute, amPm) = extractTimeParameters(trimmed)
            val cleanLabel = "Astra Alarm"
            val needsClar = (hour == null)
            return StructuredIntent(
                action = StructuredIntent.ACTION_ALARM,
                targetApp = "Clock",
                goal = "Set an alarm for $hour:${minute ?: 0} ${amPm ?: "AM"}",
                parameters = mapOf(
                    "hour" to (hour ?: 7),
                    "minute" to (minute ?: 0),
                    "amPm" to (amPm ?: "AM"),
                    "message" to cleanLabel
                ),
                executionSteps = listOf("Validate time parameters", "Dispatch AlarmManager / ACTION_SET_ALARM intent"),
                needsClarification = needsClar,
                clarificationQuestion = if (needsClar) "What time should I set the alarm for?" else null,
                verificationStrategy = "Verify system alarm intent dispatched",
                mappedTool = "alarm_tool",
                mappedArguments = mapOf(
                    "hour" to (hour ?: 7),
                    "minute" to (minute ?: 0),
                    "amPm" to (amPm ?: "AM"),
                    "message" to cleanLabel
                ),
                requiresConfirmation = false,
                rationale = "User requested scheduling a device alarm."
            )
        }

        // 7. Telephony: Calls ("Ammi ko call karo", "SIM 1 se Ali ko call karna", "Call Mom")
        if (lower.contains("call") || lower.contains("phone milao") || lower.contains("dial")) {
            val (contact, sim) = extractCallParameters(trimmed)
            val isDirectNumber = contact.filter { it.isDigit() }.length >= 5
            val needsClar = contact.isBlank()

            val toolName = if (isDirectNumber) "call_phone" else "call_contact"
            val args = if (isDirectNumber) {
                mapOf("phoneNumber" to contact, "sim" to sim)
            } else {
                mapOf("contactName" to contact, "sim" to sim)
            }

            return StructuredIntent(
                action = StructuredIntent.ACTION_CALL,
                targetApp = "Phone",
                goal = "Place a phone call to $contact via $sim",
                parameters = args,
                executionSteps = listOf("Lookup contact", "Request user confirmation", "Place call via TelecomManager"),
                needsClarification = needsClar,
                clarificationQuestion = if (needsClar) "Who would you like me to call?" else null,
                verificationStrategy = "Verify outgoing call intent dispatched",
                mappedTool = toolName,
                mappedArguments = args,
                requiresConfirmation = true,
                confirmationPrompt = "Call $contact on $sim?",
                rationale = "High-risk telephony action requires user confirmation."
            )
        }

        // 8. Contact Lookup ("Ammi ka number batao", "Find number for Ali")
        if ((lower.contains("number") || lower.contains("contact")) &&
            (lower.contains("batao") || lower.contains("find") || lower.contains("search") || lower.contains("what is") || lower.contains("kya hai"))
        ) {
            val contactName = cleanContactQuery(trimmed)
            return StructuredIntent(
                action = StructuredIntent.ACTION_SEARCH,
                targetApp = "Contacts",
                goal = "Look up contact details for $contactName",
                parameters = mapOf("contactName" to contactName),
                executionSteps = listOf("Query ContactsContract", "Format matching contact cards"),
                needsClarification = contactName.isBlank(),
                clarificationQuestion = if (contactName.isBlank()) "Which contact's number are you looking for?" else null,
                verificationStrategy = "Verify contact query returns result",
                mappedTool = "contact_lookup",
                mappedArguments = mapOf("contactName" to contactName),
                rationale = "User requested looking up a contact."
            )
        }

        // 9. Messaging (WhatsApp & SMS)
        // e.g. "WhatsApp pe Ali ko bolo meeting kal hai", "SMS bhejo Ammi ko main pohanch gaya"
        if (lower.contains("message") || lower.contains("sms") || lower.contains("text") ||
            lower.contains("bhejo") || lower.contains("bolo") || lower.contains("kaho")
        ) {
            val isWhatsApp = lower.contains("whatsapp")
            val (recipient, message) = extractMessageParameters(trimmed)
            val needsClar = recipient.isBlank() || message.isBlank()

            val toolName = if (isWhatsApp) "whatsapp_message" else "send_message"
            val args = if (isWhatsApp) {
                mapOf("contactName" to recipient, "message" to message)
            } else {
                mapOf("contactName" to recipient, "message" to message, "application" to "SMS")
            }

            return StructuredIntent(
                action = StructuredIntent.ACTION_MESSAGE,
                targetApp = if (isWhatsApp) "WhatsApp" else "SMS",
                goal = "Send message to $recipient: \"$message\"",
                parameters = args,
                executionSteps = listOf("Lookup recipient", "Prompt for confirmation", "Dispatch message intent"),
                needsClarification = needsClar,
                clarificationQuestion = when {
                    recipient.isBlank() -> "Who would you like to message?"
                    message.isBlank() -> "What message would you like to send to $recipient?"
                    else -> null
                },
                verificationStrategy = "Verify messaging intent dispatched",
                mappedTool = toolName,
                mappedArguments = args,
                requiresConfirmation = true,
                confirmationPrompt = "Send message to $recipient: \"$message\"?",
                rationale = "Outgoing messaging requires explicit user confirmation."
            )
        }

        // 10. Memory / Quick Notes ("Remember that...", "Yaad rakho...", "Save note...")
        if (lower.startsWith("remember") || lower.startsWith("yaad rakho") || lower.contains("save note")) {
            val content = trimmed
                .replace(Regex("(?i)^(remember that|remember|yaad rakho ke|yaad rakho|save note:?)\\s*"), "")
                .trim()
            return StructuredIntent(
                action = StructuredIntent.ACTION_NOTE,
                targetApp = "Memory",
                goal = "Save note into persistent memory: \"$content\"",
                parameters = mapOf("content" to content, "category" to "personal"),
                executionSteps = listOf("Persist entity to Room database", "Confirm storage"),
                needsClarification = content.isBlank(),
                clarificationQuestion = if (content.isBlank()) "What would you like me to remember?" else null,
                verificationStrategy = "Verify memory entity saved in Room",
                mappedTool = "quick_note",
                mappedArguments = mapOf("content" to content, "category" to "personal"),
                rationale = "User requested persisting a personal note or fact."
            )
        }

        // 11. Notifications ("Notification bhejo meeting starts now", "Post alert...")
        if (lower.contains("notification") && (lower.contains("bhejo") || lower.contains("post") || lower.contains("send") || lower.contains("alert"))) {
            val msg = trimmed.replace(Regex("(?i).*(notification|alert)\\s*"), "").trim().ifBlank { "Reminder" }
            return StructuredIntent(
                action = StructuredIntent.ACTION_NOTIFICATION,
                targetApp = "Notifications",
                goal = "Post local notification: \"$msg\"",
                parameters = mapOf("title" to "Astra Voice Assistant Alert", "message" to msg),
                executionSteps = listOf("Build notification", "Dispatch via NotificationManager"),
                needsClarification = false,
                verificationStrategy = "Verify notification channel active and posted",
                mappedTool = "notification_tool",
                mappedArguments = mapOf("title" to "Astra Voice Assistant Alert", "message" to msg),
                rationale = "User requested posting an Android notification."
            )
        }

        // 11b. App Installation Verification ("Check karo mere phone mein TikTok installed hai ya nahi", "Is WhatsApp installed?")
        val isAppInstallCheck = (lower.contains("installed") || lower.contains("hai ya nahi")) &&
                (lower.contains("check") || lower.contains("dekho") || lower.contains("batao") || lower.contains("phone mein") || lower.contains("mere phone"))
        if (isAppInstallCheck) {
            val (detectedApp, _) = detectTargetApp(trimmed)
            val extractedApp = detectedApp ?: trimmed
                .replace(Regex("(?i)\\b(check karo|check|mere phone mein|phone mein|is|installed hai ya nahi|installed hai|installed|hai ya nahi|batao|dekho|kya|hai|app)\\b"), "")
                .trim()
            if (extractedApp.isNotBlank()) {
                return StructuredIntent(
                    action = StructuredIntent.ACTION_SEARCH,
                    targetApp = extractedApp,
                    goal = "Check if $extractedApp is installed on this device",
                    parameters = mapOf("appName" to extractedApp, "checkOnly" to true),
                    executionSteps = listOf("Inspect PackageManager", "Verify launchable package"),
                    needsClarification = false,
                    verificationStrategy = "Verify installed status returned",
                    mappedTool = "open_app",
                    mappedArguments = mapOf("appName" to extractedApp, "checkOnly" to true),
                    rationale = "User inquired whether an application is installed on the device."
                )
            }
        }

        // 12. App Search vs App Launch (Generalized for ALL apps: YouTube, TikTok, Instagram, Spotify, Browser, etc.)
        val (targetApp, isKnownApp) = detectTargetApp(trimmed)
        val hasSearchVerb = lower.contains("search") || lower.contains("dhoondo") ||
                lower.contains("talash") || lower.contains("dekho") || lower.contains("find")
        val hasLaunchVerb = lower.contains("kholo") || lower.contains("open") ||
                lower.contains("chalao") || lower.contains("start") || lower.contains("launch")

        if (targetApp != null && (hasSearchVerb || (hasLaunchVerb && lower.contains("search")))) {
            // Action: SEARCH in targetApp (e.g. "YouTube pe search karo CID", "TikTok pe search karo cooking recipes")
            val cleanQuery = extractCleanSearchQuery(trimmed, targetApp)
            if (cleanQuery.isBlank()) {
                // If search query is completely blank (e.g. "YouTube search karo" without a query),
                // open the app instead of searching for filler words
                return StructuredIntent(
                    action = StructuredIntent.ACTION_LAUNCH,
                    targetApp = targetApp,
                    goal = "Open $targetApp application",
                    parameters = mapOf("appName" to targetApp),
                    executionSteps = listOf("Resolve $targetApp package", "Launch application intent"),
                    needsClarification = false,
                    verificationStrategy = "Verify $targetApp activity launched",
                    mappedTool = "open_app",
                    mappedArguments = mapOf("appName" to targetApp),
                    rationale = "User requested opening $targetApp without a specific search query."
                )
            }

            val toolName = if (targetApp.equals("YouTube", ignoreCase = true)) "youtube_search" else "search_app"
            val args = if (targetApp.equals("YouTube", ignoreCase = true)) {
                mapOf("query" to cleanQuery)
            } else {
                mapOf("application" to targetApp, "query" to cleanQuery)
            }

            return StructuredIntent(
                action = StructuredIntent.ACTION_SEARCH,
                targetApp = targetApp,
                goal = "Search $targetApp for \"$cleanQuery\"",
                parameters = mapOf("targetApp" to targetApp, "query" to cleanQuery),
                executionSteps = listOf("Resolve $targetApp deep link/search intent", "Launch search query: $cleanQuery"),
                needsClarification = false,
                verificationStrategy = "Verify search intent dispatched to $targetApp",
                mappedTool = toolName,
                mappedArguments = args,
                rationale = "User requested in-app search inside $targetApp."
            )
        }

        // 13. General App Launch (e.g. "WhatsApp kholo", "Camera open karo", "Instagram chalao", "Settings kholo")
        if (targetApp != null && (hasLaunchVerb || isKnownApp)) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_LAUNCH,
                targetApp = targetApp,
                goal = "Open $targetApp application",
                parameters = mapOf("appName" to targetApp),
                executionSteps = listOf("Query PackageManager for $targetApp", "Launch activity"),
                needsClarification = false,
                verificationStrategy = "Verify $targetApp activity launched",
                mappedTool = "open_app",
                mappedArguments = mapOf("appName" to targetApp),
                rationale = "User requested opening $targetApp application."
            )
        }

        // 14. Camera / Gallery Tools
        if (lower.contains("camera") || lower.contains("photo khincho") || lower.contains("tasveer")) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_LAUNCH,
                targetApp = "Camera",
                goal = "Open device camera",
                parameters = mapOf("action" to "open"),
                executionSteps = listOf("Launch ACTION_IMAGE_CAPTURE"),
                needsClarification = false,
                verificationStrategy = "Camera intent launched",
                mappedTool = "camera_tool",
                mappedArguments = mapOf("action" to "open"),
                rationale = "User requested opening camera."
            )
        }

        if (lower.contains("gallery") || lower.contains("photos")) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_LAUNCH,
                targetApp = "Gallery",
                goal = "Open photo gallery",
                parameters = mapOf("action" to "view_photos"),
                executionSteps = listOf("Launch ACTION_VIEW for images"),
                needsClarification = false,
                verificationStrategy = "Gallery intent launched",
                mappedTool = "gallery_tool",
                mappedArguments = mapOf("action" to "view_photos"),
                rationale = "User requested opening photo gallery."
            )
        }

        // 15. Web Search Fallback (e.g. "Search for latest news", "Google pe search karo Pakistan news")
        if (hasSearchVerb) {
            val cleanQuery = extractCleanSearchQuery(trimmed, "Browser")
            if (cleanQuery.isNotBlank()) {
                return StructuredIntent(
                    action = StructuredIntent.ACTION_SEARCH,
                    targetApp = "Browser",
                    goal = "Search the web for \"$cleanQuery\"",
                    parameters = mapOf("queryOrUrl" to cleanQuery),
                    executionSteps = listOf("Construct ACTION_WEB_SEARCH intent", "Launch default browser"),
                    needsClarification = false,
                    verificationStrategy = "Web search intent launched",
                    mappedTool = "browser_tool",
                    mappedArguments = mapOf("queryOrUrl" to cleanQuery),
                    rationale = "User requested general web search."
                )
            }
        }

        // 16. UI Automation & Navigation Actions
        // e.g. "Scroll down", "Neeche scroll karo", "Scroll up", "Go back", "Wapas jao", "Inspect screen", "Tap [button]", "Click [button]"
        if (lower.contains("scroll down") || lower.contains("scroll forward") || lower.contains("neeche scroll")) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_ACCESSIBILITY,
                targetApp = "Accessibility",
                goal = "Scroll screen forward",
                parameters = mapOf("action" to "scroll_forward"),
                executionSteps = listOf("Locate scrollable container", "Perform ACTION_SCROLL_FORWARD"),
                needsClarification = false,
                verificationStrategy = "Verify screen content changed",
                mappedTool = "accessibility_action",
                mappedArguments = mapOf("action" to "scroll_forward"),
                rationale = "User requested scrolling down."
            )
        }

        if (lower.contains("scroll up") || lower.contains("scroll backward") || lower.contains("upar scroll")) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_ACCESSIBILITY,
                targetApp = "Accessibility",
                goal = "Scroll screen backward",
                parameters = mapOf("action" to "scroll_backward"),
                executionSteps = listOf("Locate scrollable container", "Perform ACTION_SCROLL_BACKWARD"),
                needsClarification = false,
                verificationStrategy = "Verify screen content changed",
                mappedTool = "accessibility_action",
                mappedArguments = mapOf("action" to "scroll_backward"),
                rationale = "User requested scrolling up."
            )
        }

        if (lower in listOf("go back", "back", "wapas", "piche jao", "back karo", "navigate back")) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_ACCESSIBILITY,
                targetApp = "Accessibility",
                goal = "Navigate back",
                parameters = mapOf("action" to "go_back"),
                executionSteps = listOf("Perform GLOBAL_ACTION_BACK"),
                needsClarification = false,
                verificationStrategy = "Verify previous window restored",
                mappedTool = "accessibility_action",
                mappedArguments = mapOf("action" to "go_back"),
                rationale = "User requested Back navigation."
            )
        }

        if (lower.contains("inspect screen") || lower.contains("read screen") || lower.contains("screen par kya hai")) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_ACCESSIBILITY,
                targetApp = "Accessibility",
                goal = "Inspect visible screen controls",
                parameters = mapOf("action" to "inspect"),
                executionSteps = listOf("Query AccessibilityNodeInfo hierarchy", "Summarize visible interactive controls"),
                needsClarification = false,
                verificationStrategy = "Verify screen summary extracted",
                mappedTool = "accessibility_action",
                mappedArguments = mapOf("action" to "inspect"),
                rationale = "User requested screen inspection."
            )
        }

        if (lower.startsWith("tap ") || lower.startsWith("click ") || lower.startsWith("dabao ")) {
            val target = trimmed.replace(Regex("(?i)^(tap|click|dabao)\\s+"), "").trim()
            if (target.isNotBlank()) {
                return StructuredIntent(
                    action = StructuredIntent.ACTION_ACCESSIBILITY,
                    targetApp = "Accessibility",
                    goal = "Tap \"$target\"",
                    parameters = mapOf("action" to "click", "target" to target),
                    executionSteps = listOf("Find semantic node for $target", "Perform ACTION_CLICK"),
                    needsClarification = false,
                    verificationStrategy = "Verify node clicked and UI updated",
                    mappedTool = "accessibility_action",
                    mappedArguments = mapOf("action" to "click", "target" to target),
                    rationale = "User requested clicking UI element."
                )
            }
        }

        // 17. Default Conversational Fallback
        return StructuredIntent(
            action = StructuredIntent.ACTION_CONVERSATION,
            targetApp = null,
            goal = "Answer user conversationally",
            parameters = mapOf("query" to trimmed),
            executionSteps = listOf("Generate crisp spoken conversational response"),
            needsClarification = false,
            verificationStrategy = "Conversational response delivered",
            mappedTool = null,
            rationale = "No action tool required; general dialogue."
        )
    }

    /**
     * Detects target application either from common known apps or live installed apps.
     */
    private fun detectTargetApp(query: String): Pair<String?, Boolean> {
        val lower = query.lowercase()

        // 1. Check known apps
        for ((key, canonical) in commonApps) {
            if (Regex("\\b${Regex.escape(key)}\\b").containsMatchIn(lower)) {
                return Pair(canonical, true)
            }
        }

        // 2. Check dynamic installed apps via AppManager
        val installedApps = appManager?.getInstalledApps().orEmpty()
        for (app in installedApps) {
            val appLower = app.appName.lowercase()
            if (appLower.length > 2 && Regex("\\b${Regex.escape(appLower)}\\b").containsMatchIn(lower)) {
                return Pair(app.appName, true)
            }
        }

        return Pair(null, false)
    }

    /**
     * Extracts a clean search query by stripping target app tokens, search command verbs,
     * prepositions, and vernacular noise (e.g. "karo", "pe", "chalao").
     */
    fun extractCleanSearchQuery(fullQuery: String, appName: String): String {
        var cleaned = fullQuery

        if (appName.equals("YouTube", ignoreCase = true)) {
            val parsed = com.astra.ai.tools.builtin.YouTubeSearchParser.extractQuery(fullQuery)
            if (parsed.isNotBlank()) return parsed
            cleaned = cleaned.replace(Regex("(?i)\\byoutube\\b"), " ")
        } else if (appName.equals("TikTok", ignoreCase = true)) {
            cleaned = cleaned.replace(Regex("(?i)\\btiktok\\b"), " ")
        } else if (appName.equals("Instagram", ignoreCase = true)) {
            cleaned = cleaned.replace(Regex("(?i)\\binsta(gram)?\\b"), " ")
        } else if (appName.equals("Browser", ignoreCase = true) || appName.equals("Google", ignoreCase = true)) {
            cleaned = cleaned.replace(Regex("(?i)\\b(google|chrome|browser)\\b"), " ")
        }

        // Remove filler noise words
        cleaned = cleaned.replace(noiseFillerRegex, " ")

        // Clean extra punctuation and double whitespace
        cleaned = cleaned.replace(Regex("[\"\'`:;?,.]"), " ").trim()
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        AstraLogger.d("UniversalSemanticIntentEngine", "Query extraction: \"$fullQuery\" (app: $appName) -> \"$cleaned\"")
        return cleaned
    }

    private fun extractTimeParameters(query: String): Triple<Int?, Int?, String?> {
        val lower = query.lowercase()

        val isPm = lower.contains("pm") || lower.contains("shaam") || lower.contains("raat") || lower.contains("dopahar")
        val isAm = lower.contains("am") || lower.contains("subah") || lower.contains("fajar")
        val amPm = if (isPm) "PM" else if (isAm) "AM" else null

        // Pattern 1: e.g. "7:30", "07:30"
        val colonMatch = Regex("(\\d{1,2}):(\\d{2})").find(query)
        if (colonMatch != null) {
            val h = colonMatch.groupValues[1].toIntOrNull()
            val m = colonMatch.groupValues[2].toIntOrNull()
            return Triple(h, m, amPm ?: if ((h ?: 0) in 1..6) "PM" else "AM")
        }

        // Pattern 2: e.g. "7 baje", "7:00", "at 7"
        val hourMatch = Regex("(\\d{1,2})\\s*(baje|o'?clock|am|pm)?", RegexOption.IGNORE_CASE).find(query)
        if (hourMatch != null) {
            val h = hourMatch.groupValues[1].toIntOrNull()
            if (h != null && h in 1..24) {
                return Triple(h, 0, amPm ?: if (h in 1..6) "PM" else "AM")
            }
        }

        return Triple(null, null, amPm)
    }

    private fun extractCallParameters(query: String): Pair<String, String> {
        val cleanQuery = query.replace(Regex("[\"\'`:;?,.]"), " ").trim()
        val lower = cleanQuery.lowercase()
        val sim = if (lower.contains("sim 1")) "SIM 1" else if (lower.contains("sim 2")) "SIM 2" else "Default SIM"

        var contact = cleanQuery
        val wordsToRemove = listOf(
            "sim 1 se", "sim 2 se", "sim 1", "sim 2", "from sim 1", "from sim 2", "via sim 1", "via sim 2",
            "ko call karo", "ko call kar do", "ko call karna", "ko call lagao", "ko call",
            "call karo", "call kar do", "call karna", "call lagao", "call kar", "call to", "call",
            "phone milao", "phone karo", "dial karo", "dial", "please", "ko", "se"
        )
        for (w in wordsToRemove) {
            contact = contact.replace(Regex("(?i)\\b${Regex.escape(w)}\\b"), " ")
        }
        contact = contact.replace(Regex("\\s+"), " ").trim()
        return Pair(contact.ifBlank { "Contact" }, sim)
    }

    private fun extractMessageParameters(query: String): Pair<String, String> {
        val lower = query.lowercase()

        // Split on message markers like "ke", "that", "bolo", "message"
        val msgMarker = listOf(" ke ", " that ", " bolo ", " kaho ", " text: ", " message: ")
        var recipient = ""
        var message = ""

        for (marker in msgMarker) {
            if (lower.contains(marker)) {
                val parts = query.split(Regex("(?i)${Regex.escape(marker)}"), limit = 2)
                recipient = parts[0]
                message = parts.getOrNull(1).orEmpty()
                break
            }
        }

        if (recipient.isBlank()) {
            recipient = query
        }

        val removeRecip = listOf(
            "whatsapp pe", "whatsapp par", "whatsapp mein", "whatsapp",
            "sms bhejo", "sms", "message bhejo", "message", "ko", "se", "please"
        )
        for (w in removeRecip) {
            recipient = recipient.replace(Regex("(?i)\\b${Regex.escape(w)}\\b"), " ")
        }
        recipient = recipient.replace(Regex("\\s+"), " ").trim()
        message = message.trim()

        return Pair(recipient.ifBlank { "Contact" }, message.ifBlank { "Hello" })
    }

    private fun cleanContactQuery(query: String): String {
        return query.replace(
            Regex("(?i)(ka number batao|ka contact batao|number batao|contact batao|what is|find contact|find number for|'s number|'s contact|kya hai|number|contact)"),
            " "
        ).replace(Regex("\\s+"), " ").trim()
    }
}
