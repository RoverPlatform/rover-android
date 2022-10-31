package io.rover.campaigns.core.events.contextproviders

import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider

/**
 * Captures and adds information about the default route (ie., Wifi vs mobile data) to the [DeviceContext]
 *
 * NB.  Not to be confused with "Reachability", an iOS screen-ducking feature for thumb access.
 */
class ReachabilityContextProvider(
    applicationContext: android.content.Context
) : ContextProvider {
    private val connectionManager = applicationContext.applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION") // Using deprecated API only on legacy Android.
    private fun getNetworkInfoForType(networkType: Int): List<NetworkInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectionManager.allNetworks.mapNotNull { network ->
                connectionManager.getNetworkInfo(network)
            }.filter { networkInfo ->
                networkInfo.type == networkType
            }.toList()
        } else {
            listOfNotNull(connectionManager.getNetworkInfo(networkType))
        }
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
