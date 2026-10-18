package com.astra.ai.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.astra.ai.core.AstraLogger

/**
 * Immutable snapshot of an observed accessible UI element.
 */
data class UiNodeInfo(
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val isClickable: Boolean = false,
    val isFocusable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEnabled: Boolean = true,
    val isSelected: Boolean = false,
    val isPassword: Boolean = false,
    val bounds: Rect = Rect(),
    val children: List<UiNodeInfo> = emptyList(),
    val parentSummary: String? = null
) {
    /**
     * Determines whether this node contains or matches any text.
     */
    val displayLabel: String
        get() = text?.takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
            ?: resourceId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: className?.substringAfterLast('.')
            ?: "element"

    val isInteractive: Boolean
        get() = isClickable || isEditable || isScrollable || isFocusable
}

/**
 * Snapshot of the current visible screen hierarchy observed by AstraAccessibilityService.
 */
data class UiObservation(
    val packageName: String?,
    val activityName: String? = null,
    val rootNodes: List<UiNodeInfo> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis(),
    val isSecureSurface: Boolean = false,
    val error: String? = null
) {
    /**
     * Flattens the tree into a list of all nodes.
     */
    fun allNodes(): List<UiNodeInfo> {
        val list = mutableListOf<UiNodeInfo>()
        fun collect(node: UiNodeInfo) {
            list.add(node)
            node.children.forEach { collect(it) }
        }
        rootNodes.forEach { collect(it) }
        return list
    }

    /**
     * Finds all interactive controls visible on the screen.
     */
    fun findInteractiveNodes(): List<UiNodeInfo> {
        return allNodes().filter { it.isInteractive && it.isEnabled }
    }

    /**
     * Summary of visible labels for logging and AI reasoning.
     */
    fun summary(): String {
        val interactive = findInteractiveNodes()
        val labels = interactive.mapNotNull { node ->
            val label = node.text ?: node.contentDescription
            if (!label.isNullOrBlank()) label.trim() else null
        }.distinct().take(15)

        return if (labels.isEmpty()) {
            "Package: $packageName (No accessible interactive controls found)"
        } else {
            "Package: $packageName (${labels.size} controls: ${labels.joinToString(", ")})"
        }
    }
}

/**
 * Extracts UiObservation snapshots from Android AccessibilityNodeInfo hierarchies safely.
 */
object UiObservationExtractor {

    private const val MAX_DEPTH = 25
    private const val TAG = "UiObservationExtractor"

    /**
     * Safely parses an AccessibilityNodeInfo into an immutable UiObservation tree.
     */
    fun extract(root: AccessibilityNodeInfo?): UiObservation {
        if (root == null) {
            return UiObservation(packageName = null)
        }

        val packageName = root.packageName?.toString()
        val rootNodes = mutableListOf<UiNodeInfo>()
        var isSecure = false

        try {
            val rootInfo = parseNode(root, depth = 0, parentSummary = null)
            if (rootInfo != null) {
                rootNodes.add(rootInfo)
                if (rootInfo.isPassword) {
                    isSecure = true
                }
            }
        } catch (e: Exception) {
            AstraLogger.e(TAG, "Failed extracting accessibility hierarchy: ${e.message}", e)
        }

        return UiObservation(
            packageName = packageName,
            rootNodes = rootNodes,
            isSecureSurface = isSecure
        )
    }

    @Suppress("DEPRECATION")
    private fun parseNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        parentSummary: String?
    ): UiNodeInfo? {
        if (depth > MAX_DEPTH) return null

        val bounds = Rect()
        try {
            node.getBoundsInScreen(bounds)
        } catch (_: Exception) {}

        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val resId = node.viewIdResourceName
        val className = node.className?.toString()
        val pkgName = node.packageName?.toString()
        val isPassword = node.isPassword

        val currentSummary = text ?: contentDesc ?: resId

        val children = mutableListOf<UiNodeInfo>()
        val childCount = node.childCount
        for (i in 0 until childCount) {
            try {
                val child = node.getChild(i)
                if (child != null) {
                    val childInfo = parseNode(child, depth + 1, currentSummary)
                    if (childInfo != null) {
                        children.add(childInfo)
                    }
                    try {
                        child.recycle()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                // Ignore recycled or inaccessible child nodes
            }
        }

        return UiNodeInfo(
            text = text,
            contentDescription = contentDesc,
            resourceId = resId,
            className = className,
            packageName = pkgName,
            isClickable = node.isClickable,
            isFocusable = node.isFocusable,
            isEditable = node.isEditable,
            isScrollable = node.isScrollable,
            isEnabled = node.isEnabled,
            isSelected = node.isSelected,
            isPassword = isPassword,
            bounds = bounds,
            children = children,
            parentSummary = parentSummary
        )
    }
}
