package com.astra.ai.voice

import com.astra.ai.tools.builtin.YouTubeSearchParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the core bug fixes:
 * 1. YouTube search parser never extracts "karo" or filler verbs.
 * 2. Clean query extraction from natural multilingual commands (Urdu, Roman Urdu, English).
 */
class VoiceRecognitionAndYouTubeFixTest {

    @Test
    fun testYouTubeKholoAurSearchKaroDoesNotExtractKaro() {
        val query1 = "YouTube kholo aur Android 16 search karo"
        val result1 = YouTubeSearchParser.extractQuery(query1)
        assertEquals("Android 16", result1)

        val query2 = "YouTube kholo aur Android news search karo"
        val result2 = YouTubeSearchParser.extractQuery(query2)
        assertEquals("Android news", result2)
    }

    @Test
    fun testYouTubeParSearchKaroVariants() {
        val q1 = "YouTube par Android news search karo"
        val r1 = YouTubeSearchParser.extractQuery(q1)
        assertEquals("Android news", r1)

        val q2 = "YouTube par search karo Android news"
        val r2 = YouTubeSearchParser.extractQuery(q2)
        assertEquals("Android news", r2)

        val q3 = "YouTube pe cooking recipe dhoondo"
        val r3 = YouTubeSearchParser.extractQuery(q3)
        assertEquals("cooking recipe", r3)
    }

    @Test
    fun testYouTubeWithoutQueryReturnsEmpty() {
        // Should launch YouTube, not search for "karo" or "search karo"
        val q1 = "YouTube par search karo"
        val r1 = YouTubeSearchParser.extractQuery(q1)
        assertEquals("", r1)

        val q2 = "YouTube search karo"
        val r2 = YouTubeSearchParser.extractQuery(q2)
        assertEquals("", r2)

        val q3 = "YouTube kholo"
        val r3 = YouTubeSearchParser.extractQuery(q3)
        assertEquals("", r3)

        val q4 = "Open YouTube"
        val r4 = YouTubeSearchParser.extractQuery(q4)
        assertEquals("", r4)
    }

    @Test
    fun testEnglishYouTubeQueries() {
        val q1 = "Open YouTube and search for Flutter widgets"
        val r1 = YouTubeSearchParser.extractQuery(q1)
        assertEquals("Flutter widgets", r1)

        val q2 = "Search cats on YouTube"
        val r2 = YouTubeSearchParser.extractQuery(q2)
        assertEquals("cats", r2)

        val q3 = "YouTube search Kotlin programming"
        val r3 = YouTubeSearchParser.extractQuery(q3)
        assertEquals("Kotlin programming", r3)
    }

    @Test
    fun testNoTrailingOrLeadingVerbsInExtractedQuery() {
        val inputs = listOf(
            "YouTube kholo aur Android news search karo",
            "YouTube par Pakistani drama OST search kar do",
            "YouTube kholo Python tutorial search karna",
            "YouTube par search karo latest gadgets"
        )
        for (input in inputs) {
            val extracted = YouTubeSearchParser.extractQuery(input)
            assertFalse("Extracted should not contain 'karo': $extracted", extracted.contains("karo", ignoreCase = true))
            assertFalse("Extracted should not contain 'search': $extracted", extracted.contains("search", ignoreCase = true))
            assertTrue("Extracted query should be meaningful", extracted.isNotBlank())
        }
    }
}
