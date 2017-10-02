package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Context
import io.rover.rover.services.network.putProp
import org.json.JSONObject

/**
 * Outgoing JSON DTO transformation for [Context]s, as submitted to the Rover GraphQL API.
 */
fun Context.asJson(): JSONObject {
    return JSONObject().apply {
        val props = listOf(
            Context::appBuild,
            Context::appName,
            Context::appNamespace,
            Context::appVersion,
            Context::carrierName,
            Context::deviceManufacturer,
            Context::deviceModel,
            Context::isCellularEnabled,
            Context::isLocationServicesEnabled,
            Context::isWifiEnabled,
            Context::locationAuthorization,
            Context::localeLanguage,
            Context::localeRegion,
            Context::localeScript,
            Context::notificationAuthorization,
            Context::operatingSystemName,
            Context::operatingSystemVersion,
            Context::pushEnvironment,
            Context::pushToken,
            Context::radio,
            Context::screenWidth,
            Context::screenHeight,
            Context::sdkVersion,
            Context::timeZone
        )

        props.forEach { putProp(this@asJson, it) }
    }
}

/**
 * Incoming JSON DTO transformation for [Context]s, as received from the Rover GraphQL API.
 */
fun Context.Companion.decodeJson(json: JSONObject): Context {
    return Context(
        appBuild = json.getString("appBuild"),
        appName = json.getString("appName"),
        appNamespace = json.getString("appNamespace"),
        appVersion = json.getString("appVersion"),
        carrierName = json.getString("carrierName"),
        deviceManufacturer = json.getString("deviceManufacturer"),
        deviceModel = json.getString("deviceModel"),
        isCellularEnabled = json.getBoolean("isCellularEnabled"),
        isLocationServicesEnabled = json.getBoolean("isLocationServicesEnabled"),
        isWifiEnabled = json.getBoolean("isWifiEnabled"),
        locationAuthorization = json.getString("locationAuthorization"),
        localeLanguage = json.getString("localeLanguage"),
        localeRegion = json.getString("localeRegion"),
        localeScript = json.getString("localeScript"),
        notificationAuthorization = json.getString("notificationAuthorization"),
        operatingSystemName = json.getString("operatingSystemName"),
        operatingSystemVersion = json.getString("operatingSystemVersion"),
        pushEnvironment = json.getString("pushEnvironment"),
        pushToken = json.getString("pushToken"),
        radio = json.getString("radio"),
        screenWidth = json.getInt("screenWidth"),
        screenHeight = json.getInt("screenHeight"),
        sdkVersion = json.getString("sdkVersion"),
        timeZone = json.getString("timeZone")
    )
}
