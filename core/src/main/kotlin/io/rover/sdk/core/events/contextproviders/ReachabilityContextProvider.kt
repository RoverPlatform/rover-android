/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.core.events.contextproviders

import android.net.ConnectivityManager
import android.net.NetworkInfo
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
