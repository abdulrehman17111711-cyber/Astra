package com.astra.ai.apps

import android.content.Context

/**
 * AppManager interface bridging backward compatibility while delegating
 * to the rich AppDiscoveryManager engine.
 */
interface AppManager {
    fun getInstalledApps(): List<InstalledApp>
    fun findAppByName(name: String): InstalledApp?
    fun launchApp(packageName: String): Boolean
    fun launchAppDetailed(nameOrPackage: String): AppLaunchResult
    fun refreshInstalledApps(onComplete: ((List<InstalledApp>) -> Unit)? = null)
    val discoveryManager: AppDiscoveryManager
}

class AndroidAppManager(
    private val context: Context,
    override val discoveryManager: AppDiscoveryManager = AppDiscoveryManager(context)
) : AppManager {

    override fun getInstalledApps(): List<InstalledApp> {
        val current = discoveryManager.installedApps.value
        if (current.isNotEmpty()) return current
        discoveryManager.refreshInstalledApps()
        return discoveryManager.installedApps.value
    }

    override fun findAppByName(name: String): InstalledApp? {
        return discoveryManager.findApp(name)
    }

    override fun launchApp(packageName: String): Boolean {
        val result = discoveryManager.launchApp(packageName)
        return result is AppLaunchResult.Success
    }

    override fun launchAppDetailed(nameOrPackage: String): AppLaunchResult {
        return discoveryManager.launchApp(nameOrPackage)
    }

    override fun refreshInstalledApps(onComplete: ((List<InstalledApp>) -> Unit)?) {
        discoveryManager.refreshInstalledApps(onComplete)
    }
}
