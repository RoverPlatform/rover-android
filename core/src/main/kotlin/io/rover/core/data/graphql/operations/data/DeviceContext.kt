package io.rover.core.data.graphql.operations.data

import io.rover.core.data.domain.DeviceContext
import io.rover.core.data.graphql.putProp
import io.rover.core.data.graphql.safeGetString
import io.rover.core.data.graphql.safeOptBoolean
import io.rover.core.data.graphql.safeOptInt
import io.rover.core.data.graphql.safeOptString
import io.rover.core.platform.whenNotNull
import org.json.JSONObject

/**
 * Outgoing JSON DTO transformation for [DeviceContext]s, as submitted to the Rover GraphQL API.
 */
internal fun DeviceContext.asJson(): JSONObject {
    return JSONObject().apply {
        val props = listOf(
            DeviceContext::appBuild,
            DeviceContext::appIdentifier,
            DeviceContext::deviceIdentifier,
            DeviceContext::appVersion,
            DeviceContext::carrierName,
            DeviceContext::deviceManufacturer,
            DeviceContext::deviceModel,
            DeviceContext::deviceName,
            DeviceContext::isCellularEnabled,
            DeviceContext::isLocationServicesEnabled,
            DeviceContext::isWifiEnabled,
            DeviceContext::locationAuthorization,
            DeviceContext::localeLanguage,
            DeviceContext::localeRegion,
            DeviceContext::localeScript,
            DeviceContext::operatingSystemName,
            DeviceContext::operatingSystemVersion,
            DeviceContext::pushEnvironment,
            DeviceContext::pushToken,
            DeviceContext::radio,
            DeviceContext::screenWidth,
            DeviceContext::screenHeight,
            DeviceContext::timeZone,
            DeviceContext::isBluetoothEnabled,
            DeviceContext::sdkVersion,
            DeviceContext::isTestDevice
        )

        props.forEach { putProp(this@asJson, it) }

        putProp(this@asJson, DeviceContext::attributes, "attributes") { it.encodeJson() }

        putProp(this@asJson, DeviceContext::notificationAuthorization, "notificationAuthorization") { it?.encodeJson() ?: JSONObject.NULL }
    }
}

/**
 * Incoming JSON DTO transformation for [DeviceContext]s, as received from the Rover GraphQL API.
 */
internal fun DeviceContext.Companion.decodeJson(json: JSONObject): DeviceContext {
    return DeviceContext(
        appBuild = json.safeOptString("appBuild"),
        appIdentifier = json.safeOptString("appIdentifier"),
        appVersion = json.safeOptString("appVersion"),
        carrierName = json.safeOptString("carrierName"),
        deviceIdentifier = json.safeOptString("deviceIdentifier"),
        deviceManufacturer = json.safeOptString("deviceManufacturer"),
        deviceModel = json.safeOptString("deviceModel"),
        deviceName = json.safeOptString("deviceName"),
        isCellularEnabled = json.safeOptBoolean("isCellularEnabled"),
        isLocationServicesEnabled = json.safeOptBoolean("isLocationServicesEnabled"),
        isWifiEnabled = json.safeOptBoolean("isWifiEnabled"),
        locationAuthorization = json.safeOptString("locationAuthorization"),
        localeLanguage = json.safeOptString("localeLanguage"),
        localeRegion = json.safeOptString("localeRegion"),
        localeScript = json.safeOptString("localeScript"),
        notificationAuthorization = json.safeOptString("notificationAuthorization").whenNotNull { DeviceContext.NotificationAuthorization.decodeJson(it) },
        operatingSystemName = json.safeOptString("operatingSystemName"),
        operatingSystemVersion = json.safeOptString("operatingSystemVersion"),
        pushEnvironment = json.safeOptString("pushEnvironment"),
        pushToken = json.safeOptString("pushToken"),
        radio = json.safeOptString("radio"),
        screenWidth = json.safeOptInt("screenWidth"),
        screenHeight = json.safeOptInt("screenHeight"),
        sdkVersion = json.safeOptString("sdkVersion"),
        timeZone = json.safeOptString("timeZone"),
        isBluetoothEnabled = json.safeOptBoolean("isBluetoothEnabled"),
        attributes = json.getJSONObject("attributes").toFlatAttributesHash(),
        isTestDevice = json.safeOptBoolean("isTestDevice")
    )
}

internal fun DeviceContext.NotificationAuthorization.Companion.decodeJson(value: String): DeviceContext.NotificationAuthorization {
    return DeviceContext.NotificationAuthorization.values().firstOrNull { it.wireFormat == value } ?: throw Exception("Unknown notification authorization value: ")
}

internal fun DeviceContext.NotificationAuthorization.encodeJson(): String {
    return this.wireFormat
}

private fun Map<String, String>.encodeJson(): JSONObject {
    return JSONObject(this)
}

private fun JSONObject.asStringHash(): Map<String, String> {
    return this.keys().asSequence().map { key ->
        Pair(key, this@asStringHash.safeGetString(key))
    }.associate { it }
}