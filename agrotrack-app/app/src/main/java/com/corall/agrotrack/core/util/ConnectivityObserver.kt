package com.corall.agrotrack.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkStatus { Available, Unavailable, Losing, Lost }

@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val networkStatus: Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network)          { trySend(NetworkStatus.Available) }
            override fun onLosing(network: Network, maxMsToLive: Int) { trySend(NetworkStatus.Losing) }
            override fun onLost(network: Network)               { trySend(NetworkStatus.Lost) }
            override fun onUnavailable()                        { trySend(NetworkStatus.Unavailable) }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        manager.registerNetworkCallback(request, callback)

        val initial = if (isConnected()) NetworkStatus.Available else NetworkStatus.Unavailable
        trySend(initial)

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun isConnected(): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps    = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
