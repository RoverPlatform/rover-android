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

package io.rover.sdk.core.data.domain

import java.util.Date

/**
 * A Rover context: describes the device and general situation when an [Event] is generated.
 */
data class DeviceContext(
    val trackingMode: String?,
    val appBuild: String?,
    val appIdentifier: String?,
    val appVersion: String?,
    val carrierName: String?,
    val deviceManufacturer: String?,
    val deviceModel: String?,

    val deviceIdentifier: String?,

    /**
     * The hostname/user set name of the device.
     *
     * On Android, some device vendors allow you to set a device name with a sane model model name
     * as the default, although this typically manifests as the Bluetooth name, whereas the hostname
     * remains set as 'android-xxx'.  So, we use the Bluetooth device name.
     */
    val deviceName: String?,

    val isCellularEnabled: Boolean?,
    val isLocationServicesEnabled: Boolean?,
    val isWifiEnabled: Boolean?,
    val locationAuthorization: String?,
    val localeLanguage: String?,
    val localeRegion: String?,
    val localeScript: String?,

    val isDarkModeEnabled: Boolean?,

    // TODO: enum type
    val notificationAuthorization: NotificationAuthorization?,

    val operatingSystemName: String?,
    val operatingSystemVersion: String?,
    val pushEnvironment: String?,
    val pushToken: PushToken?,
    val radio: String?,

    /**
     * Screen width, in dp.
     */
    val screenWidth: Int?,

    /**
     * Screen height, in dp.
     */
    val screenHeight: Int?,

    /**
     * The version of the SDK.
     */
    val sdkVersion: String?,

    val timeZone: String?,

    /**
     * Is a bluetooth radio present and enabled?
     */
    val isBluetoothEnabled: Boolean?,

    val isTestDevice: Boolean?,

    /**
     * Device location.
     *
     * Only populated if the Rover Location module is installed.
     */
    val location: Location?,

    /**
     * Device attributes.
     */
    val userInfo: Attributes,

    /**
     * A platform specific advertising identifier.
     */
    val advertisingIdentifier: String?,

    /**
     * A list of conversion tags
     */
    val conversions: List<String>,

    /**
     * A timestamp indicating when the device was last seen by the user.
     * This is an ISO 8601 formatted string.
     */
    val lastSeen: String?
) {
    companion object {
        internal fun blank(): DeviceContext {
            return DeviceContext(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, false, null, null, hashMapOf(), null, listOf(), null
            )
        }
    }

    enum class NotificationAuthorization(
        val wireFormat: String
    ) {
        Authorized("authorized"),
        Denied("denied");

        companion object
    }

    data class PushToken(
        val value: String,
        val timestamp: Date
    ) {
        companion object
    }
}
