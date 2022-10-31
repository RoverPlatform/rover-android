package io.rover.campaigns.core.events.contextproviders

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider

/**
 * Captures and adds the mobile carrier and data connection details to a [DeviceContext].
 */
class TelephonyContextProvider(
    private val applicationContext: android.content.Context
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

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        val targetSdkVersion = applicationContext.applicationInfo.targetSdkVersion

        val networkTypeName = if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED || targetSdkVersion < 30) {
            getNetworkTypeName(telephonyManager.networkType)
        } else {
            null
        }

        return deviceContext.copy(radio = networkTypeName, carrierName = telephonyManager.networkOperatorName)
    }
}
