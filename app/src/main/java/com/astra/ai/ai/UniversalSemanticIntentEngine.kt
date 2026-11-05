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

        // 7. Telephony: Calls ("منان کو کال کرو", "Ammi ko call karo", "SIM 1 se Ali ko call karna", "Call Mom")
        val isCallQuery = lower.contains("call") || lower.contains("phone milao") || lower.contains("phone karo") ||
                lower.contains("phone lagao") || lower.contains("phone call") || lower.contains("dial") ||
                lower.contains("کال") || lower.contains("فون ملاؤ") || lower.contains("فون کرو") ||
                lower.contains("فون کریں") || lower.contains("ملاؤ") || lower.contains("rabta karo")
        if (isCallQuery) {
            val (contact, sim) = extractCallParameters(trimmed)
            val isDirectNumber = contact.filter { it.isDigit() }.length >= 5
            val needsClar = contact.isBlank()

            val toolName = if (isDirectNumber) "call_phone" else "call_contact"
            val args = if (isDirectNumber) {
                mapOf("phoneNumber" to contact, "contactName" to contact, "contactOrNumber" to contact, "sim" to sim, "simPreference" to sim)
            } else {
                mapOf("contactName" to contact, "contactOrNumber" to contact, "sim" to sim, "simPreference" to sim)
            }

            return StructuredIntent(
                action = StructuredIntent.ACTION_CALL,
                targetApp = "Phone",
                goal = "Place a phone call to $contact via $sim",
                parameters = args,
                executionSteps = listOf("Lookup contact", "Place call via TelecomManager"),
                needsClarification = needsClar,
                clarificationQuestion = if (needsClar) "Who would you like me to call?" else null,
                verificationStrategy = "Verify outgoing call intent dispatched",
                mappedTool = toolName,
                mappedArguments = args,
                requiresConfirmation = true,
                confirmationPrompt = "Call $contact on $sim?",
                rationale = "Direct telephony call execution requested by user."
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

        // 9. Messaging (WhatsApp, WhatsApp Business & SMS)
        // e.g. "واٹس ایپ پہ منان کو میسج کرو کہ جلدی گھر ا جائے", "WhatsApp pe Ali ko bolo meeting kal hai"
        if (lower.contains("message") || lower.contains("sms") || lower.contains("text") ||
            lower.contains("bhejo") || lower.contains("bolo") || lower.contains("kaho") ||
            lower.contains("میسج") || lower.contains("بھیجو") || lower.contains("کہو") || lower.contains("بولو")
        ) {
            val isBusiness = lower.contains("business") || lower.contains("بزنس")
            val isWhatsApp = lower.contains("whatsapp") || lower.contains("واٹس ایپ") || !lower.contains("sms")
            val (recipient, message) = extractMessageParameters(trimmed)
            val needsClar = recipient.isBlank() || message.isBlank()

            val targetAppName = if (isBusiness) "WhatsApp Business" else if (isWhatsApp) "WhatsApp" else "SMS"
            val toolName = if (isWhatsApp) "whatsapp_message" else "send_message"
            val args = if (isWhatsApp) {
                mapOf(
                    "contactName" to recipient,
                    "recipient" to recipient,
                    "message" to message,
                    "appType" to targetAppName
                )
            } else {
                mapOf("contactName" to recipient, "recipient" to recipient, "message" to message, "application" to "SMS")
            }

            return StructuredIntent(
                action = StructuredIntent.ACTION_MESSAGE,
                targetApp = targetAppName,
                goal = "Send $targetAppName message to $recipient: \"$message\"",
                parameters = args,
                executionSteps = listOf("Lookup recipient", "Prepare and dispatch message intent"),
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
                rationale = "Outgoing messaging prepared or sent."
            )
        }

        // 9b. Device Screen Wake / Unlock ("موبائل انلاک کرو", "موبائل ان لاک کرو", "Unlock mobile", "Unlock phone")
        if (lower.contains("unlock") || lower.contains("انلاک") || lower.contains("ان لاک") ||
            (lower.contains("screen") && (lower.contains("on") || lower.contains("wake")))
        ) {
            return StructuredIntent(
                action = StructuredIntent.ACTION_SYSTEM_CONTROL,
                targetApp = "System",
                goal = "Wake display and request device unlock",
                parameters = emptyMap(),
                executionSteps = listOf("Acquire wake lock", "Dismiss keyguard"),
                needsClarification = false,
                verificationStrategy = "Verify screen interactive",
                mappedTool = "device_unlock",
                mappedArguments = emptyMap(),
                requiresConfirmation = false,
                rationale = "User requested waking and unlocking the device screen."
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

        // 11a. Read Notifications ("Read my notifications", "Notifications parho", "Koi naya notification aaya hai?", "نوٹیفکیشن پڑھو")
        val isReadNotificationQuery = (lower.contains("notification") || lower.contains("نوٹیفکیشن") || lower.contains("نوٹیفیکیشن")) &&
                (lower.contains("read") || lower.contains("check") || lower.contains("parho") || lower.contains("sunao") ||
                 lower.contains("kya") || lower.contains("aaya") || lower.contains("پڑھو") || lower.contains("سناؤ") || lower.contains("چیک") ||
                 lower.contains("show") || lower.contains("dikhao") || lower.contains("دکھاؤ") || lower.trim() == "notifications")
        if (isReadNotificationQuery) {
            val appFilter = when {
                lower.contains("whatsapp") || lower.contains("واٹس ایپ") -> "WhatsApp"
                lower.contains("message") || lower.contains("sms") -> "Messages"
                lower.contains("gmail") -> "Gmail"
                else -> null
            }
            return StructuredIntent(
                action = StructuredIntent.ACTION_READ_NOTIFICATION,
                targetApp = appFilter ?: "System",
                goal = "Read incoming notifications${if (appFilter != null) " for $appFilter" else ""}",
                parameters = mapOf("appName" to (appFilter ?: "")),
                executionSteps = listOf("Query AccessibilityState captured notifications", "Format spoken summary"),
                needsClarification = false,
                verificationStrategy = "Verify captured notifications read to user",
                mappedTool = "read_notifications",
                mappedArguments = mapOf("appName" to (appFilter ?: "")),
                requiresConfirmation = false,
                rationale = "User requested reading incoming notifications via accessibility service."
            )
        }

        // 11b. Notifications ("Notification bhejo meeting starts now", "Post alert...")
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

        val isSim2 = lower.contains("sim 2") || lower.contains("سم 2") || lower.contains("سم ٹو") || lower.contains("دوسری سم")
        val isSim1 = lower.contains("sim 1") || lower.contains("سم 1") || lower.contains("سم ون") || lower.contains("پہلی سم")
        val sim = if (isSim2) "SIM 2" else if (isSim1) "SIM 1" else "Default SIM"

        var contact = cleanQuery

        // 1. Remove composite phrases first (longest phrases first)
        val phrasePatterns = listOf(
            "(?i)from\\s+sim\\s+[12]",
            "(?i)via\\s+sim\\s+[12]",
            "(?i)sim\\s+[12]\\s+se",
            "(?i)sim\\s+[12]",
            "سم\\s*[12]\\s*سے",
            "سم\\s*ٹو\\s*سے",
            "سم\\s*ون\\s*سے",
            "دوسری\\s*سم\\s*سے",
            "پہلی\\s*سم\\s*سے",
            "سم\\s*[12]",
            "سم\\s*ٹو",
            "سم\\s*ون",
            "(?i)ko\\s+call\\s+(?:karo|kar\\s+do|karna|lagao|kar)",
            "(?i)call\\s+(?:karo|kar\\s+do|karna|lagao|kar)",
            "(?i)phone\\s+(?:milao|karo|lagao)",
            "(?i)dial\\s+karo",
            "کو\\s+کال\\s+(?:کرو|کر\\s+دو|ملا\\s+دو|ملاؤ|کرنا|کیجیے)",
            "کال\\s+(?:کرو|کر\\s+دو|ملا\\s+دو|ملاؤ|کرنا|کیجیے)",
            "فون\\s+(?:ملاؤ|کرو|ملا\\s+دو|لگاؤ)",
            "(?i)^call\\s+",
            "(?i)^dial\\s+",
            "^کال\\s+کرو\\s+",
            "^فون\\s+ملاؤ\\s+"
        )

        for (pattern in phrasePatterns) {
            contact = contact.replace(Regex(pattern), " ")
        }

        // 2. Remove standalone postpositions and connectors strictly with word/whitespace boundaries
        val standaloneWords = listOf(
            "(?i)(?:^|\\s+)ko(?:\\s+|$)",
            "(?i)(?:^|\\s+)se(?:\\s+|$)",
            "(?i)(?:^|\\s+)call(?:\\s+|$)",
            "(?i)(?:^|\\s+)dial(?:\\s+|$)",
            "(?i)(?:^|\\s+)please(?:\\s+|$)",
            "(?:^|\\s+)کو(?:\\s+|$)",
            "(?:^|\\s+)سے(?:\\s+|$)",
            "(?:^|\\s+)کال(?:\\s+|$)",
            "(?:^|\\s+)فون(?:\\s+|$)"
        )
        for (wRegex in standaloneWords) {
            contact = contact.replace(Regex(wRegex), " ")
        }

        contact = contact.replace(Regex("\\s+"), " ").trim()
        return Pair(contact.ifBlank { "Contact" }, sim)
    }

    private fun extractMessageParameters(query: String): Pair<String, String> {
        val cleanQuery = query.trim()

        // Split on message markers like "کہ", "ke", "that", "bolo", "message"
        val splitRegex = Regex("(?i)(\\s+کہ\\s+|\\s+کہ|\\s+ke\\s+|\\s+that\\s+|\\s+bolo\\s+|\\s+kaho\\s+|\\s+text:\\s*|\\s+message:\\s*)")
        var recipient = ""
        var message = ""

        if (splitRegex.containsMatchIn(cleanQuery)) {
            val parts = cleanQuery.split(splitRegex, limit = 2)
            recipient = parts[0]
            message = parts.getOrNull(1).orEmpty()
        } else {
            recipient = cleanQuery
        }

        val removeRecip = listOf(
            "واٹس ایپ اوپن کرو اور", "واٹس ایپ بزنس اوپن کرو اور",
            "واٹس ایپ بزنس پہ", "واٹس ایپ بزنس پر", "واٹس ایپ بزنس میں", "واٹس ایپ بزنس",
            "واٹس ایپ پہ", "واٹس ایپ پر", "واٹس ایپ میں", "واٹس ایپ",
            "whatsapp business pe", "whatsapp business par", "whatsapp business",
            "whatsapp pe", "whatsapp par", "whatsapp mein", "whatsapp",
            "کو میسج کرو", "کو میسج بھیجو", "میسج کرو", "میسج بھیجو", "میسج",
            "message bhejo", "message karo", "message", "sms bhejo", "sms",
            "میرے بھائی", "میرا بھائی", "بھائی", "mera bhai", "mere bhai",
            "open whatsapp and", "open whatsapp", "please", "ko", "se", "کو", "سے"
        )
        for (w in removeRecip) {
            recipient = recipient.replace(Regex("(?i)\\b${Regex.escape(w)}\\b"), " ")
            recipient = recipient.replace(w, " ")
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
