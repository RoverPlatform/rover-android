package io.rover.sdk.core.events.contextproviders

import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider

/**
 * Captures and adds information about the default route (ie., Wifi vs mobile data) to the [DeviceContext]
 *
 * NB.  Not to be confused with "Reachability", an iOS screen-ducking feature for thumb access.
 */
class ReachabilityContextProvider(
    applicationContext: android.content.Context
) : ContextProvider {
    private val connectionManager = applicationContext.applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun getNetworkInfoForType(networkType: Int): List<NetworkInfo> {
        return connectionManager.allNetworks.mapNotNull { network ->
            connectionManager.getNetworkInfo(network)
        }.filter { networkInfo ->
            networkInfo.type == networkType
        }.toList()
    }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        val wifis = getNetworkInfoForType(ConnectivityManager.TYPE_WIFI)

        val basebands = getNetworkInfoForType(ConnectivityManager.TYPE_MOBILE)

        return deviceContext.copy(
            isWifiEnabled = wifis.any { it.isAvailable },
            isCellularEnabled = basebands.any { it.isAvailable }
        )
    }
}