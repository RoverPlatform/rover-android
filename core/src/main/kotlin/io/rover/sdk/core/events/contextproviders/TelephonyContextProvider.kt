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

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.privacy.PrivacyService

/**
 * Captures and adds the mobile carrier and data connection details to a [DeviceContext].
 */
class TelephonyContextProvider(
    private val applicationContext: android.content.Context,
    private val privacyService: PrivacyService
) : ContextProvider {
    private val telephonyManager = applicationContext.applicationContext.getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * Duplicated from TelephonyManager.java, as it has not passed Android API Council review.
     */
    private fun getNetworkTypeName(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "CDMA - EvDo rev. 0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "CDMA - EvDo rev. A"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "CDMA - EvDo rev. B"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "CDMA - 1xRTT"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "CDMA - eHRPD"
            TelephonyManager.NETWORK_TYPE_IDEN -> "iDEN"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD_SCDMA"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            else -> "UNKNOWN: $type"
        }
    }

    @SuppressLint("MissingPermission")
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) {
            return deviceContext
        }

        val targetSdkVersion = applicationContext.applicationInfo.targetSdkVersion

        val networkTypeName = if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED || targetSdkVersion < 30) {
            getNetworkTypeName(telephonyManager.networkType)
        } else {
            null
        }

        return deviceContext.copy(radio = networkTypeName, carrierName = telephonyManager.networkOperatorName)
    }
}
