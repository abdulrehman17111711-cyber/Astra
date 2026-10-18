package com.astra.ai.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.astra.ai.core.AstraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result of an application launch attempt.
 */
sealed class AppLaunchResult {
    data class Success(val app: InstalledApp, val message: String) : AppLaunchResult()
    data class NotInstalled(val requestedName: String, val message: String) : AppLaunchResult()
    data class CannotLaunch(val app: InstalledApp, val message: String) : AppLaunchResult()
    data class Error(val message: String, val cause: Throwable? = null) : AppLaunchResult()
}

/**
 * AppDiscoveryManager
 * Dynamic application discovery system using Android PackageManager and package visibility rules.
 * Maintains a live cache of installed launchable apps, detects installation/removal changes,
 * and provides robust fuzzy and alias lookup.
 */
class AppDiscoveryManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val packageManager: PackageManager = context.packageManager
    private val mutex = Mutex()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var packageChangeReceiver: BroadcastReceiver? = null

    init {
        registerPackageChangeReceiver()
        refreshInstalledApps()
    }

    /**
     * Scans the system for launchable applications asynchronously and updates the cache.
     */
    fun refreshInstalledApps(onComplete: ((List<InstalledApp>) -> Unit)? = null) {
        val immediateApps = discoverAppsInternal()
        _installedApps.value = immediateApps
        onComplete?.invoke(immediateApps)

        scope.launch {
            mutex.withLock {
                _isRefreshing.value = true
                try {
                    val apps = discoverAppsInternal()
                    _installedApps.value = apps
                    AstraLogger.i("AppDiscoveryManager", "Discovered ${apps.size} installed launchable applications")
                } catch (e: Exception) {
                    AstraLogger.e("AppDiscoveryManager", "Failed to discover installed apps", e)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun refreshInstalledAppsSync(): List<InstalledApp> {
        val apps = discoverAppsInternal()
        _installedApps.value = apps
        return apps
    }

    /**
     * Synchronous discovery logic executed on background thread.
     */
    private fun discoverAppsInternal(): List<InstalledApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(launcherIntent, 0)
            }
        } catch (e: Exception) {
            AstraLogger.e("AppDiscoveryManager", "Error querying intent activities", e)
            emptyList()
        }

        val discovered = mutableListOf<InstalledApp>()
        val seenPackages = mutableSetOf<String>()

        for (info in resolveInfos) {
            val activityInfo = info.activityInfo ?: continue
            val packageName = activityInfo.packageName ?: continue
            if (seenPackages.contains(packageName)) continue
            seenPackages.add(packageName)

            try {
                val appLabel = info.loadLabel(packageManager).toString().trim().ifBlank {
                    activityInfo.name.substringAfterLast(".")
                }

                val icon = try {
                    info.loadIcon(packageManager)
                } catch (e: Exception) {
                    null
                }

                val packageInfo: PackageInfo? = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0)
                    }
                } catch (e: Exception) {
                    null
                }

                val versionName = packageInfo?.versionName
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo?.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo?.versionCode?.toLong()
                }

                val appInfo = packageInfo?.applicationInfo ?: activityInfo.applicationInfo
                val isEnabled = appInfo?.enabled ?: true

                // Capabilities
                val capabilities = mutableSetOf<AppCapability>()
                capabilities.add(AppCapability.LAUNCHABLE)

                // Detect share capability
                if (hasShareTarget(packageName)) {
                    capabilities.add(AppCapability.SHARE_TARGET)
                }

                // Detect deep link capability
                if (hasDeepLinkTarget(packageName)) {
                    capabilities.add(AppCapability.DEEP_LINK)
                }

                // Detect accessibility/known integrations
                val aliases = getAliasesForPackage(packageName, appLabel)
                if (aliases.isNotEmpty()) {
                    capabilities.add(AppCapability.KNOWN_INTEGRATION)
                }

                val launchMethod = when {
                    !activityInfo.name.isNullOrBlank() -> LaunchMethod.MAIN_LAUNCHER_ACTIVITY
                    packageManager.getLaunchIntentForPackage(packageName) != null -> LaunchMethod.PACKAGE_LAUNCH_INTENT
                    hasDeepLinkTarget(packageName) -> LaunchMethod.DEEP_LINK_INTENT
                    else -> LaunchMethod.UNAVAILABLE
                }

                discovered.add(
                    InstalledApp(
                        appName = appLabel,
                        packageName = packageName,
                        launchActivity = activityInfo.name,
                        launchMethod = launchMethod,
                        icon = icon,
                        versionName = versionName,
                        versionCode = versionCode,
                        isEnabled = isEnabled,
                        isLaunchable = true,
                        capabilities = capabilities,
                        aliases = aliases
                    )
                )
            } catch (e: Exception) {
                AstraLogger.w("AppDiscoveryManager", "Could not inspect package $packageName: ${e.message}")
            }
        }

        return discovered.sortedBy { it.appName.lowercase() }
    }

    private fun hasShareTarget(packageName: String): Boolean {
        return try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                setPackage(packageName)
            }
            packageManager.queryIntentActivities(sendIntent, 0).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun hasDeepLinkTarget(packageName: String): Boolean {
        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(packageName)
            }
            packageManager.queryIntentActivities(viewIntent, 0).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun getAliasesForPackage(packageName: String, appLabel: String): List<String> {
        val list = mutableListOf<String>()
        val pkgLower = packageName.lowercase()
        val nameLower = appLabel.lowercase()

        when {
            pkgLower.contains("whatsapp") -> {
                list.addAll(listOf("what's app", "whats app", "whatsapp messenger", "watsapp"))
            }
            pkgLower.contains("youtube") -> {
                list.addAll(listOf("you tube", "yt", "youtube app"))
            }
            pkgLower.contains("instagram") -> {
                list.addAll(listOf("insta", "ig"))
            }
            pkgLower.contains("spotify") -> {
                list.addAll(listOf("spotfy", "music player"))
            }
            pkgLower.contains("camera") -> {
                list.addAll(listOf("cam", "photo camera"))
            }
            pkgLower.contains("settings") -> {
                list.addAll(listOf("setting", "system settings", "device settings"))
            }
            pkgLower.contains("chrome") -> {
                list.addAll(listOf("google chrome", "browser", "web browser"))
            }
            pkgLower.contains("gallery") || pkgLower.contains("photos") -> {
                list.addAll(listOf("photos", "photo gallery", "images"))
            }
            pkgLower.contains("clock") || pkgLower.contains("alarm") -> {
                list.addAll(listOf("alarm", "timer", "stopwatch"))
            }
        }
        return list
    }

    /**
     * Finds an installed app using exact name, normalized name, partial name,
     * package name, or known aliases.
     */
    fun findApp(nameOrPackage: String): InstalledApp? {
        val query = nameOrPackage.trim()
        if (query.isBlank()) return null
        var apps = _installedApps.value
        if (apps.isEmpty()) {
            apps = discoverAppsInternal()
            _installedApps.value = apps
        }
        val normalizedQuery = InstalledApp.normalize(query)

        // 1. Exact package name match
        apps.firstOrNull { it.packageName.equals(query, ignoreCase = true) }?.let { return it }

        // 2. Exact app name match (case-insensitive)
        apps.firstOrNull { it.appName.equals(query, ignoreCase = true) }?.let { return it }

        // 3. Normalized app name exact match
        apps.firstOrNull { InstalledApp.normalize(it.appName) == normalizedQuery }?.let { return it }

        // 4. Exact match on aliases
        apps.firstOrNull { app ->
            app.aliases.any { InstalledApp.normalize(it) == normalizedQuery }
        }?.let { return it }

        // 5. Name contains query
        apps.firstOrNull { InstalledApp.normalize(it.appName).contains(normalizedQuery) }?.let { return it }

        // 6. Query contains app name (e.g. "open WhatsApp now")
        apps.firstOrNull {
            val normName = InstalledApp.normalize(it.appName)
            normName.length > 2 && normalizedQuery.contains(normName)
        }?.let { return it }

        // 7. Alias contains query or vice versa
        apps.firstOrNull { app ->
            app.aliases.any { alias ->
                val normAlias = InstalledApp.normalize(alias)
                normAlias.contains(normalizedQuery) || (normAlias.length > 2 && normalizedQuery.contains(normAlias))
            }
        }?.let { return it }

        // 8. Package name substring match
        return apps.firstOrNull { it.packageName.lowercase().contains(normalizedQuery) }
    }

    /**
     * Robust app launch: Resolves launch intent, checks status, launches, and verifies.
     */
    fun launchApp(nameOrPackage: String): AppLaunchResult {
        val app = findApp(nameOrPackage)
        if (app == null) {
            return AppLaunchResult.NotInstalled(
                requestedName = nameOrPackage,
                message = "\"$nameOrPackage\" is not installed."
            )
        }

        if (!app.isEnabled) {
            return AppLaunchResult.CannotLaunch(
                app = app,
                message = "${app.appName} is installed but disabled on your device."
            )
        }

        return try {
            val intent = when (app.launchMethod) {
                LaunchMethod.MAIN_LAUNCHER_ACTIVITY -> {
                    if (!app.launchActivity.isNullOrBlank()) {
                        Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            component = android.content.ComponentName(app.packageName, app.launchActivity)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else {
                        packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                }
                LaunchMethod.PACKAGE_LAUNCH_INTENT -> {
                    packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                LaunchMethod.DEEP_LINK_INTENT -> {
                    Intent(Intent.ACTION_VIEW).apply {
                        setPackage(app.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                LaunchMethod.UNAVAILABLE -> null
            } ?: packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent != null) {
                context.startActivity(intent)
                AppLaunchResult.Success(
                    app = app,
                    message = "Opening ${app.appName}..."
                )
            } else {
                AppLaunchResult.CannotLaunch(
                    app = app,
                    message = "I found ${app.appName}, but Android did not provide a launchable activity."
                )
            }
        } catch (e: Exception) {
            AstraLogger.e("AppDiscoveryManager", "Failed to launch ${app.packageName}", e)
            AppLaunchResult.Error(
                message = "Could not launch ${app.appName}: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Registers a broadcast receiver to listen for application installation, removal,
     * or update broadcasts to auto-refresh the registry.
     */
    private fun registerPackageChangeReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }

            packageChangeReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action ?: return
                    val pkgName = intent.data?.schemeSpecificPart ?: ""
                    AstraLogger.i("AppDiscoveryManager", "Package broadcast received: $action for $pkgName")
                    refreshInstalledApps()
                }
            }

            context.registerReceiver(packageChangeReceiver, filter)
        } catch (e: Exception) {
            AstraLogger.w("AppDiscoveryManager", "Could not register package change broadcast receiver: ${e.message}")
        }
    }

    /**
     * Clean up receiver when application terminates.
     */
    fun release() {
        try {
            packageChangeReceiver?.let { context.unregisterReceiver(it) }
            packageChangeReceiver = null
        } catch (e: Exception) {
            // Ignored
        }
    }
}
