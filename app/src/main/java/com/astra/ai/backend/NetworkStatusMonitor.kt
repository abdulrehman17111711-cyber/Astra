package com.astra.ai.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkStatus {
    ONLINE,
    OFFLINE
}

interface NetworkMonitor {
    val status: StateFlow<NetworkStatus>
    val isOnline: Boolean
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _status = MutableStateFlow(checkCurrentStatus())
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    override val isOnline: Boolean
        get() = _status.value == NetworkStatus.ONLINE

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _status.value = NetworkStatus.ONLINE
                }

                override fun onLost(network: Network) {
                    _status.value = NetworkStatus.OFFLINE
                }
            }
        )
    }

    private fun checkCurrentStatus(): NetworkStatus {
        val manager = connectivityManager ?: return NetworkStatus.OFFLINE
        val activeNetwork = manager.activeNetwork ?: return NetworkStatus.OFFLINE
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus.OFFLINE
        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            NetworkStatus.ONLINE
        } else {
            NetworkStatus.OFFLINE
        }
    }
}
