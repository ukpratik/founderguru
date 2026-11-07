// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the device has an internet-capable network (US-011 offline states). */
interface Connectivity {
    val isOnline: StateFlow<Boolean>
}

/** The default network's INTERNET capability, kept current by a callback for the app's lifetime (LLD §5). */
class ConnectivityMonitor(context: Context) : Connectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _isOnline = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }
        })
    }

    private fun currentlyOnline(): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
