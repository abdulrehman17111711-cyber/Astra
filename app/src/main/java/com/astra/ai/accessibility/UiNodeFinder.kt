package com.astra.ai.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.astra.ai.core.AstraLogger
import java.util.Locale

/**
 * Strict priority tiers for accessibility node matching.
 */
enum class MatchPriority(val tier: Int) {
    RESOURCE_ID(1),
    CONTENT_DESCRIPTION(2),
    EXACT_TEXT(3),
    NORMALIZED_TEXT(4),
    PARTIAL_TEXT(5),
    CLASS_TYPE(6),
    CONTEXTUAL(7),
    COORDINATES(8)
}

/**
 * Result representing a discovered matching UI node with its audit priority and confidence score.
 */
data class NodeMatchResult(
    val node: AccessibilityNodeInfo?,
    val snapshot: UiNodeInfo,
    val priority: MatchPriority,
    val score: Float,
    val reason: String
) : Comparable<NodeMatchResult> {
    override fun compareTo(other: NodeMatchResult): Int {
        // Lower tier number means higher priority (1 before 2)
        if (this.priority.tier != other.priority.tier) {
            return this.priority.tier.compareTo(other.priority.tier)
        }
        // Higher score within the same tier comes first
        return other.score.compareTo(this.score)
    }
}

/**
 * Semantic Node Finder adhering strictly to Part 7 matching priorities:
 * 1. resource ID
 * 2. contentDescription
 * 3. exact visible text
 * 4. normalized text
 * 5. partial text
 * 6. class/type
 * 7. contextual relationships
 * 8. coordinates only as an absolute last resort
 */
object UiNodeFinder {

    private const val TAG = "UiNodeFinder"

    // Semantic term dictionary for controls
    private val SEARCH_TERMS = setOf(
        "search", "find", "magnifying", "glass", "query", "explore", "search button",
        "search box", "search field", "dhoondo", "khojo", "talaash"
    )

    private val SEND_TERMS = setOf(
        "send", "submit", "enter", "go", "done", "confirm", "bhejo"
    )

    private val BACK_TERMS = setOf(
        "back", "close", "cancel", "clear", "dismiss", "exit", "navigate up"
    )

    private val PROFILE_TERMS = setOf(
        "profile", "account", "user", "me", "avatar", "my account"
    )

    /**
     * Finds the best matching node in the tree according to strict Part 7 priorities.
     */
    fun findBestMatch(root: AccessibilityNodeInfo?, targetQuery: String): NodeMatchResult? {
        val matches = findAllMatches(root, targetQuery)
        return matches.firstOrNull()
    }

    /**
     * Traverses the node tree and evaluates candidates against the target query.
     */
    fun findAllMatches(root: AccessibilityNodeInfo?, targetQuery: String): List<NodeMatchResult> {
        if (root == null || targetQuery.isBlank()) return emptyList()

        val candidates = mutableListOf<NodeMatchResult>()
        val normalizedQuery = normalize(targetQuery)
        val expandedQueries = expandSemanticTerms(normalizedQuery)

        fun inspect(node: AccessibilityNodeInfo, parentSummary: String?) {
            // Security constraint: NEVER target password / sensitive credentials
            if (node.isPassword) return

            val bounds = Rect()
            try {
                node.getBoundsInScreen(bounds)
            } catch (_: Exception) {}

            val text = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()
            val resId = node.viewIdResourceName
            val className = node.className?.toString()

            val snapshot = UiNodeInfo(
                text = text,
                contentDescription = contentDesc,
                resourceId = resId,
                className = className,
                packageName = node.packageName?.toString(),
                isClickable = node.isClickable,
                isFocusable = node.isFocusable,
                isEditable = node.isEditable,
                isScrollable = node.isScrollable,
                isEnabled = node.isEnabled,
                isSelected = node.isSelected,
                isPassword = node.isPassword,
                bounds = bounds,
                parentSummary = parentSummary
            )

            val match = evaluateNode(node, snapshot, normalizedQuery, expandedQueries, parentSummary)
            if (match != null) {
                candidates.add(match)
            }

            // Recurse children
            val count = node.childCount
            val currentSummary = text ?: contentDesc ?: resId
            for (i in 0 until count) {
                try {
                    val child = node.getChild(i)
                    if (child != null) {
                        inspect(child, currentSummary)
                        // Do not recycle immediately here as caller might need matching reference
                    }
                } catch (_: Exception) {}
            }
        }

        try {
            inspect(root, null)
        } catch (e: Exception) {
            AstraLogger.e(TAG, "Error evaluating node tree: ${e.message}", e)
        }

        return candidates.sorted()
    }

    /**
     * Evaluates a node following the strict priority order 1 to 8.
     */
    private fun evaluateNode(
        node: AccessibilityNodeInfo,
        snapshot: UiNodeInfo,
        normalizedQuery: String,
        expandedQueries: Set<String>,
        parentSummary: String?
    ): NodeMatchResult? {
        val resId = snapshot.resourceId
        val contentDesc = snapshot.contentDescription
        val text = snapshot.text
        val className = snapshot.className

        // 1. PRIORITY 1: Resource ID
        if (!resId.isNullOrBlank()) {
            val resPart = resId.substringAfterLast('/').lowercase(Locale.ROOT)
            for (query in expandedQueries) {
                if (resPart == query || resPart == "${query}_button" || resPart.contains(query)) {
                    val score = if (resPart == query) 1.0f else 0.9f
                    return NodeMatchResult(
                        node = node,
                        snapshot = snapshot,
                        priority = MatchPriority.RESOURCE_ID,
                        score = score,
                        reason = "Matched resource ID: $resId (query: $query)"
                    )
                }
            }
        }

        // 2. PRIORITY 2: Content Description
        if (!contentDesc.isNullOrBlank()) {
            val normDesc = normalize(contentDesc)
            for (query in expandedQueries) {
                if (normDesc == query) {
                    return NodeMatchResult(
                        node = node,
                        snapshot = snapshot,
                        priority = MatchPriority.CONTENT_DESCRIPTION,
                        score = 1.0f,
                        reason = "Exact match on contentDescription: \"$contentDesc\""
                    )
                } else if (normDesc.contains(query)) {
                    return NodeMatchResult(
                        node = node,
                        snapshot = snapshot,
                        priority = MatchPriority.CONTENT_DESCRIPTION,
                        score = 0.85f,
                        reason = "Partial match on contentDescription: \"$contentDesc\""
                    )
                }
            }
        }

        // 3. PRIORITY 3: Exact Visible Text
        if (!text.isNullOrBlank()) {
            if (text == normalizedQuery || text.equals(normalizedQuery, ignoreCase = false)) {
                return NodeMatchResult(
                    node = node,
                    snapshot = snapshot,
                    priority = MatchPriority.EXACT_TEXT,
                    score = 1.0f,
                    reason = "Exact visible text match: \"$text\""
                )
            }
        }

        // 4. PRIORITY 4: Normalized Text
        if (!text.isNullOrBlank()) {
            val normText = normalize(text)
            for (query in expandedQueries) {
                if (normText == query) {
                    return NodeMatchResult(
                        node = node,
                        snapshot = snapshot,
                        priority = MatchPriority.NORMALIZED_TEXT,
                        score = 0.9f,
                        reason = "Normalized text match: \"$text\""
                    )
                }
            }
        }

        // 5. PRIORITY 5: Partial Text
        if (!text.isNullOrBlank()) {
            val normText = normalize(text)
            for (query in expandedQueries) {
                if (normText.contains(query) || query.contains(normText)) {
                    val score = 0.7f * (minOf(normText.length, query.length).toFloat() / maxOf(normText.length, query.length))
                    return NodeMatchResult(
                        node = node,
                        snapshot = snapshot,
                        priority = MatchPriority.PARTIAL_TEXT,
                        score = score,
                        reason = "Partial text match: \"$text\" contains \"$query\""
                    )
                }
            }
        }

        // 6. PRIORITY 6: Class / Type
        if (!className.isNullOrBlank()) {
            val lowerClass = className.lowercase(Locale.ROOT)
            val isSearchQuery = expandedQueries.any { it in SEARCH_TERMS }
            if (isSearchQuery && (snapshot.isEditable || lowerClass.contains("edittext") || lowerClass.contains("searchview"))) {
                return NodeMatchResult(
                    node = node,
                    snapshot = snapshot,
                    priority = MatchPriority.CLASS_TYPE,
                    score = 0.6f,
                    reason = "Class type match for editable search field: $className"
                )
            }
        }

        // 7. PRIORITY 7: Contextual Relationships
        if (!parentSummary.isNullOrBlank()) {
            val normParent = normalize(parentSummary)
            for (query in expandedQueries) {
                if (normParent.contains(query) && snapshot.isInteractive) {
                    return NodeMatchResult(
                        node = node,
                        snapshot = snapshot,
                        priority = MatchPriority.CONTEXTUAL,
                        score = 0.5f,
                        reason = "Contextual match: parent contains \"$query\""
                    )
                }
            }
        }

        // 8. Coordinates as absolute last resort is not matched here without coordinate parameters
        return null
    }

    /**
     * Expands semantic query terms so "Search", "Find", "Magnifying glass" all resolve appropriately.
     */
    fun expandSemanticTerms(query: String): Set<String> {
        val set = mutableSetOf(query)
        val terms = query.split(" ").filter { it.isNotBlank() }

        if (terms.any { it in SEARCH_TERMS } || query.contains("search") || query.contains("find") || query.contains("magnifying")) {
            set.addAll(SEARCH_TERMS)
        }

        if (terms.any { it in SEND_TERMS } || query.contains("send") || query.contains("submit")) {
            set.addAll(SEND_TERMS)
        }

        if (terms.any { it in BACK_TERMS } || query.contains("back") || query.contains("close")) {
            set.addAll(BACK_TERMS)
        }

        if (terms.any { it in PROFILE_TERMS } || query.contains("profile") || query.contains("account")) {
            set.addAll(PROFILE_TERMS)
        }

        return set
    }

    /**
     * Normalizes text by trimming, lowercasing, and stripping punctuation.
     */
    fun normalize(text: String): String {
        return text.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Specifically locates a scrollable container for navigation fallback.
     */
    fun findScrollableContainer(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isScrollable) return root

        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null) {
                val found = findScrollableContainer(child)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Specifically identifies a search input or edit field on the screen.
     */
    fun findSearchInputField(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val matches = findAllMatches(root, "search")
        // Prefer editable nodes
        return matches.firstOrNull { it.snapshot.isEditable }?.node
            ?: matches.firstOrNull()?.node
    }
}
