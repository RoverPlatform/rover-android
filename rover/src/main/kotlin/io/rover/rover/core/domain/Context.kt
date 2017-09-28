package io.rover.rover.core.domain

/**
 * A Rover context: describes the device and general situation when an [Event] is generated.
 */
data class Context(
    val appBuild: String?,
    val appName: String?,
    val appNamespace: String?,
    val appVersion: String?,
    val carrierName: String?,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val isCellularEnabled: Boolean?,
    val isLocationServicesEnabled: Boolean?,
    val isWifiEnabled: Boolean?,
    val locationAuthorization: String?,
    val localeLanguage: String?,
    val localeRegion: String?,
    val localeScript: String?,
    val notificationAuthorization: String?,
    val operatingSystemName: String?,
    val operatingSystemVersion: String?,
    val pushEnvironment: String?,
    val pushToken: String?,
    val radio: String?,
    val screenWidth: Int?,
    val screenHeight: Int?,
    val sdkVersion: String?,
    val timeZone: String?
) {
    companion object
}
