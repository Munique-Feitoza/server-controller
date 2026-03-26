package com.pocketnoc.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConnectivityStatus {
    Available, Unavailable, Losing, Lost
}

class NetworkConnectivityObserver(
    private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun observe(): Flow<ConnectivityStatus> {
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    trySend(ConnectivityStatus.Available)
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)
                    trySend(ConnectivityStatus.Losing)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    trySend(ConnectivityStatus.Lost)
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    trySend(ConnectivityStatus.Unavailable)
                }
            }

            // Report current status immediately
            val currentNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
            val isValid = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            trySend(if (isValid) ConnectivityStatus.Available else ConnectivityStatus.Unavailable)

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            try {
                connectivityManager.registerNetworkCallback(request, callback)
            } catch (e: Exception) {
                trySend(ConnectivityStatus.Unavailable)
            }

            awaitClose {
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // Ignore if already unregistered
                }
            }
        }.distinctUntilChanged()
    }
}
