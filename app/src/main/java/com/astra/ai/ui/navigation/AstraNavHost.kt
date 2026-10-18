package com.astra.ai.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.astra.ai.AstraApplication
import com.astra.ai.ui.screens.history.HistoryScreen
import com.astra.ai.ui.screens.main.MainAssistantScreen
import com.astra.ai.ui.screens.memory.MemoryScreen
import com.astra.ai.ui.screens.onboarding.OnboardingScreen
import com.astra.ai.ui.screens.permissions.PermissionsScreen
import com.astra.ai.ui.screens.settings.SettingsScreen
import com.astra.ai.ui.screens.splash.SplashScreen

/**
 * Main Navigation Host for Astra AI.
 * Handles Splash -> Main Assistant -> Sub-consoles (History, Memory, Permissions, Settings, Onboarding).
 */
@Composable
fun AstraNavHost(
    app: AstraApplication,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AstraScreen.Splash.route,
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Splash Screen
        composable(AstraScreen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(AstraScreen.MainAssistant.route) {
                        popUpTo(AstraScreen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // 2. Main Voice-First Assistant Console
        composable(AstraScreen.MainAssistant.route) {
            MainAssistantScreen(
                controller = app.assistantController,
                voiceSessionManager = app.voiceSessionManager,
                wakeActivationManager = app.wakeActivationManager,
                onNavigateToHistory = { navController.navigate(AstraScreen.History.route) },
                onNavigateToMemory = { navController.navigate(AstraScreen.Memory.route) },
                onNavigateToSettings = { navController.navigate(AstraScreen.Settings.route) },
                onNavigateToOnboarding = { navController.navigate(AstraScreen.Onboarding.route) }
            )
        }

        // 3. Memory Sub-console
        composable(AstraScreen.Memory.route) {
            BackHandler { navController.popBackStack() }
            MemoryScreen(
                memoryRepository = app.memoryRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 4. Interaction History Sub-console
        composable(AstraScreen.History.route) {
            BackHandler { navController.popBackStack() }
            HistoryScreen(
                historyRepository = app.historyRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 5. Hardware Permissions Sub-console
        composable(AstraScreen.Permissions.route) {
            BackHandler { navController.popBackStack() }
            PermissionsScreen(
                permissionManager = app.permissionManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 6. Settings Sub-console
        composable(AstraScreen.Settings.route) {
            BackHandler { navController.popBackStack() }
            SettingsScreen(
                settingsRepository = app.settingsRepository,
                textToSpeechManager = app.textToSpeechManager,
                wakeActivationManager = app.wakeActivationManager,
                permissionManager = app.permissionManager,
                toolRegistry = app.toolRegistry,
                onNavigateToDiscoveredApps = { navController.navigate(AstraScreen.DiscoveredApps.route) },
                onNavigateToPrivacy = { navController.navigate(AstraScreen.Privacy.route) },
                onNavigateToAccessibilityDisclosure = { navController.navigate(AstraScreen.AccessibilityDisclosure.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 7. 7-Step Interactive Onboarding Flow
        composable(AstraScreen.Onboarding.route) {
            BackHandler { navController.popBackStack() }
            OnboardingScreen(
                permissionManager = app.permissionManager,
                onFinishOnboarding = { navController.popBackStack() }
            )
        }

        // 8. Discovered Applications Debug & Management Console
        composable(AstraScreen.DiscoveredApps.route) {
            BackHandler { navController.popBackStack() }
            com.astra.ai.ui.screens.apps.AppDiscoveryScreen(
                discoveryManager = app.appManager.discoveryManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 9. Privacy & Zero-Trust Screen
        composable(AstraScreen.Privacy.route) {
            BackHandler { navController.popBackStack() }
            com.astra.ai.ui.screens.privacy.PrivacyScreen(
                settingsRepository = app.settingsRepository,
                memoryRepository = app.memoryRepository,
                historyRepository = app.historyRepository,
                wakeActivationManager = app.wakeActivationManager,
                onNavigateToAccessibilityDisclosure = { navController.navigate(AstraScreen.AccessibilityDisclosure.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 10. Accessibility Disclosure Screen
        composable(AstraScreen.AccessibilityDisclosure.route) {
            BackHandler { navController.popBackStack() }
            com.astra.ai.ui.screens.accessibility.AccessibilityDisclosureScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
