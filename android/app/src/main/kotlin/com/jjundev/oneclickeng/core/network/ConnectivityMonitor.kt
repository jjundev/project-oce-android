package com.jjundev.oneclickeng.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ambient online/offline signal (M3-08, H7/P8). The offline banner component ([OneClickOfflineBanner])
 * and the home CTA both need a connectivity source but neither owns detection — this seam fills that
 * gap. Exposes a hot [StateFlow] so both the global banner and the home CTA disable read the same value.
 */
interface ConnectivityMonitor {
    /** `true` when a validated internet-capable network is available. Seeds from the current network. */
    val isOnline: StateFlow<Boolean>
}

/**
 * [ConnectivityManager.registerNetworkCallback]-backed implementation. The callback maintains the set
 * of available networks so a transient loss on one transport (e.g. Wi‑Fi → cellular handoff) does not
 * flip offline while another network is still up. Shared eagerly on the app scope so the value is warm
 * before the home screen first reads it.
 */
@Singleton
class AndroidConnectivityMonitor
    @Inject
    constructor(
        @ApplicationContext context: Context,
        appScope: CoroutineScope,
    ) : ConnectivityMonitor {
        private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

        override val isOnline: StateFlow<Boolean> =
            callbackFlow {
                val available = mutableSetOf<Network>()
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            available += network
                            trySend(true)
                        }

                        override fun onLost(network: Network) {
                            available -= network
                            trySend(available.isNotEmpty())
                        }
                    }
                val request =
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                trySend(currentlyOnline())
                connectivityManager?.registerNetworkCallback(request, callback)
                awaitClose { connectivityManager?.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
                .stateIn(appScope, SharingStarted.Eagerly, currentlyOnline())

        private fun currentlyOnline(): Boolean {
            val capabilities =
                connectivityManager?.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    @Binds
    @Singleton
    abstract fun bindConnectivityMonitor(impl: AndroidConnectivityMonitor): ConnectivityMonitor
}
