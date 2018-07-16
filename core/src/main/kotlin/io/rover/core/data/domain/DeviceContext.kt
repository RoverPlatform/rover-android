package io.rover.core.data.domain

/**
 * A Rover context: describes the device and general situation when an [Event] is generated.
 */
data class DeviceContext(
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

    // TODO: enum type
    val notificationAuthorization: NotificationAuthorization?,

    val operatingSystemName: String?,
    val operatingSystemVersion: String?,
    val pushEnvironment: String?,
    val pushToken: String?,
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
     * Device attributes.
     */
    val attributes: Attributes
) {
    companion object {
        internal fun blank(): DeviceContext {
            return DeviceContext(
                null, null, null, null, null, null,
                null, null,null, null, null,null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, false, hashMapOf()
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
}
