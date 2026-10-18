package com.astra.ai.apps

/**
 * Capabilities that an installed application on Android can support.
 */
enum class AppCapability(val displayName: String, val description: String) {
    LAUNCHABLE("Launchable", "Can be launched directly with a main activity"),
    ACCESSIBILITY_INTERACTION("Accessibility", "Supports accessibility inspection and UI automation"),
    DEEP_LINK("Deep Link", "Accepts web/custom URI intents"),
    SHARE_TARGET("Share Target", "Can receive shared content and messages"),
    KNOWN_INTEGRATION("Known Integration", "Has recognized Astra assistant deep integration"),
    UNKNOWN("Standard App", "Discovered application with standard capabilities")
}
