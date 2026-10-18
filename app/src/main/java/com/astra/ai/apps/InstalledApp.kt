package com.astra.ai.apps

import android.graphics.drawable.Drawable

/**
 * Method available to launch an application on the Android device.
 */
enum class LaunchMethod {
    /**
     * Explicit launch via Main Launcher Activity component (e.g., ComponentName).
     */
    MAIN_LAUNCHER_ACTIVITY,

    /**
     * Standard package launcher intent returned by PackageManager.getLaunchIntentForPackage.
     */
    PACKAGE_LAUNCH_INTENT,

    /**
     * Deep link or custom URI view intent.
     */
    DEEP_LINK_INTENT,

    /**
     * Application has no launchable component or intent available.
     */
    UNAVAILABLE
}

/**
 * Rich model representing an application discovered on the Android system.
 */
data class InstalledApp(
    val appName: String,
    val packageName: String,
    val launchActivity: String? = null,
    val launchMethod: LaunchMethod = LaunchMethod.MAIN_LAUNCHER_ACTIVITY,
    val icon: Drawable? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val isEnabled: Boolean = true,
    val isLaunchable: Boolean = true,
    val capabilities: Set<AppCapability> = setOf(AppCapability.LAUNCHABLE),
    val aliases: List<String> = emptyList()
) {
    /**
     * Checks if this app matches a search query using exact, normalized, partial,
     * package name, or configured aliases.
     */
    fun matchesQuery(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return false
        if (packageName.equals(trimmed, ignoreCase = true) || packageName.contains(trimmed, ignoreCase = true)) return true

        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return false

        val normalizedName = normalize(appName)
        if (normalizedName == normalizedQuery) return true
        if (normalizedName.contains(normalizedQuery)) return true

        // Match package name
        if (packageName.lowercase().contains(normalizedQuery) ||
            packageName.lowercase().replace(".", " ").contains(normalizedQuery) ||
            packageName.lowercase().replace(".", "").contains(normalizedQuery.replace(" ", ""))) {
            return true
        }

        // Match aliases
        for (alias in aliases) {
            val normalizedAlias = normalize(alias)
            if (normalizedAlias == normalizedQuery || normalizedAlias.contains(normalizedQuery)) {
                return true
            }
        }

        return false
    }

    companion object {
        /**
         * Normalizes app names for fuzzy lookup:
         * e.g. "what's app" -> "whatsapp", "WhatsApp Messenger" -> "whatsapp messenger"
         */
        fun normalize(text: String): String {
            return text.lowercase()
                .replace("what's app", "whatsapp")
                .replace("whats app", "whatsapp")
                .replace("what’s app", "whatsapp")
                .replace("'", "")
                .replace("’", "")
                .replace("-", " ")
                .replace("_", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
