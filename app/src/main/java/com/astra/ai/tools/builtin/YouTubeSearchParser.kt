package com.astra.ai.tools.builtin

/**
 * Robust parser for extracting real YouTube video search queries from multilingual user phrasing
 * (English, Roman Urdu/Hindi, vernacular mixtures).
 *
 * Prevents extracting filler words like "karo", "search karo", or trailing command verbs as query.
 *
 * Examples:
 * - "YouTube kholo aur Android 16 search karo" -> "Android 16"
 * - "YouTube par Android news search karo" -> "Android news"
 * - "YouTube par search karo Android news" -> "Android news"
 * - "YouTube par search karo" -> "" (launch YouTube without search)
 * - "YouTube kholo" -> "" (launch YouTube)
 * - "Open YouTube and search for Flutter widgets" -> "Flutter widgets"
 * - "Search cats on YouTube" -> "cats"
 * - "YouTube search karo" -> ""
 */
object YouTubeSearchParser {

    private val SEARCH_KEYWORDS = listOf(
        "search karo", "search kar do", "search karna", "search",
        "dhoondo", "khojo", "find", "play"
    )

    private val VERB_BLACKLIST = setOf(
        "karo", "kar do", "karna", "search", "search karo", "dhoondo",
        "khojo", "aur", "and", "youtube", "par", "pe", "chalao", "kholo", "open",
        "play", "video", "videos"
    )

    fun extractQuery(rawText: String): String {
        val cleanText = rawText.replace(Regex("[\"\'`:;?,.]"), " ").trim()
        val lower = cleanText.lowercase()

        var text = cleanText

        // 0. Remove leading wake words (e.g. "Hey Astra", "Astra", etc.)
        text = text.replace(Regex("(?i)^(hey astra|hello astra|ok astra|okay astra|astra|suno astra)\\s*[,:]?\\s*"), "")

        // 1. Remove all references to YouTube and surrounding prepositions/launchers
        text = text.replace(Regex("(?i)\\b(on|in|par|pe)\\s+youtube\\b"), " ")
        text = text.replace(Regex("(?i)\\byoutube\\s+(par|pe|to|in|on|for)\\b"), " ")
        text = text.replace(Regex("(?i)\\byoutube\\b"), " ")
        text = text.replace(Regex("(?i)\\b(kholo|khol kar|khol ke|khol karke|khol|open kar do|open karo|open karke|open|chalao|launch|start|karke)\\b"), " ")
        text = text.replace(Regex("(?i)\\b(aur|and)\\b"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()

        // 2. Strip leading search directives:
        // "search karo Android news" -> "Android news"
        // "search for Android news" -> "Android news"
        text = text.replace(Regex("(?i)^(search\\s+karo|search\\s+kar\\s+do|search\\s+karna|search\\s+for|search\\s+in|search|dhoondo|khojo|find|play)\\s*(ke|that|:)?\\s*"), "")

        // 3. Strip trailing search directives:
        // "Android news search karo" -> "Android news"
        text = text.replace(Regex("(?i)\\s*(search\\s+karo|search\\s+kar\\s+do|search\\s+karna|search\\s+for|search|dhoondo|khojo|find)$"), "")

        // 4. Strip trailing helper verbs and conversational particles:
        text = text.replace(Regex("(?i)\\s+(karo|kar do|karna|chalao|dikhayein|dikhao|please)$"), "")
        text = text.replace(Regex("(?i)^(aur|and|par|pe|for)\\s+"), "")
        text = text.replace(Regex("\\s+"), " ").trim()

        // 5. Final guard against remaining filler words
        if (text.isBlank() || text.lowercase() in VERB_BLACKLIST) {
            return ""
        }

        return text
    }
}
