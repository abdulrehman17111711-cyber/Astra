package com.astra.ai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation routes for Astra AI.
 */
sealed class AstraScreen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null,
    val isBottomNavTab: Boolean = false
) {
    data object Splash : AstraScreen(
        route = "splash",
        title = "Splash",
        icon = null,
        isBottomNavTab = false
    )

    data object MainAssistant : AstraScreen(
        route = "main_assistant",
        title = "Assistant",
        icon = Icons.Default.Assistant,
        isBottomNavTab = true
    )

    data object Memory : AstraScreen(
        route = "memory",
        title = "Memory",
        icon = Icons.Default.Psychology,
        isBottomNavTab = true
    )

    data object History : AstraScreen(
        route = "history",
        title = "History",
        icon = Icons.Default.History,
        isBottomNavTab = true
    )

    data object Permissions : AstraScreen(
        route = "permissions",
        title = "Permissions",
        icon = Icons.Default.Security,
        isBottomNavTab = true
    )

    data object Settings : AstraScreen(
        route = "settings",
        title = "Settings",
        icon = Icons.Default.Settings,
        isBottomNavTab = true
    )

    data object Onboarding : AstraScreen(
        route = "onboarding",
        title = "About Astra Voice Assistant",
        icon = Icons.Default.Info,
        isBottomNavTab = false
    )

    data object DiscoveredApps : AstraScreen(
        route = "discovered_apps",
        title = "Installed Apps",
        icon = null,
        isBottomNavTab = false
    )

    data object Privacy : AstraScreen(
        route = "privacy",
        title = "Privacy",
        icon = Icons.Default.Security,
        isBottomNavTab = false
    )

    data object AccessibilityDisclosure : AstraScreen(
        route = "accessibility_disclosure",
        title = "Accessibility Disclosure",
        icon = null,
        isBottomNavTab = false
    )

    companion object {
        val bottomNavItems: List<AstraScreen> = listOf(
            MainAssistant,
            Memory,
            History,
            Permissions,
            Settings
        )
    }
}
