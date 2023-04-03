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

package io.rover.sdk.core.data.graphql.operations.data

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.data.domain.DeviceContext.PushToken
import io.rover.sdk.core.data.domain.Location
import io.rover.sdk.core.data.graphql.getDate
import io.rover.sdk.core.data.graphql.getStringIterable
import io.rover.sdk.core.data.graphql.putProp
import io.rover.sdk.core.data.graphql.safeGetString
import io.rover.sdk.core.data.graphql.safeOptBoolean
import io.rover.sdk.core.data.graphql.safeOptInt
import io.rover.sdk.core.data.graphql.safeOptString
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.platform.whenNotNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Outgoing JSON DTO transformation for [DeviceContext]s, as submitted to the Rover GraphQL API.
 */
internal fun DeviceContext.asJson(dateFormatting: DateFormattingInterface): JSONObject {
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
            DeviceContext::isDarkModeEnabled,
            DeviceContext::operatingSystemName,
            DeviceContext::operatingSystemVersion,
            DeviceContext::pushEnvironment,
            DeviceContext::radio,
            DeviceContext::screenWidth,
            DeviceContext::screenHeight,
            DeviceContext::timeZone,
            DeviceContext::isBluetoothEnabled,
            DeviceContext::sdkVersion,
            DeviceContext::isTestDevice,
            DeviceContext::advertisingIdentifier,
            DeviceContext::lastSeen
        )

        props.forEach { putProp(this@asJson, it) }

        putProp(this@asJson, DeviceContext::conversions, "conversions") { JSONArray(it) }

        putProp(this@asJson, DeviceContext::userInfo, "userInfo") { it.encodeJson() }

        putProp(this@asJson, DeviceContext::notificationAuthorization, "notificationAuthorization") { it?.encodeJson() ?: JSONObject.NULL }

        putProp(this@asJson, DeviceContext::pushToken, "pushToken") { it?.encodeJson(dateFormatting) ?: JSONObject.NULL }

        putProp(this@asJson, DeviceContext::location, "location") { it?.encodeJson(dateFormatting) ?: JSONObject.NULL }
    }
}

/**
 * Incoming JSON DTO transformation for [DeviceContext]s, as received from the Rover GraphQL API.
 */
internal fun DeviceContext.Companion.decodeJson(json: JSONObject, dateFormatting: DateFormattingInterface): DeviceContext {
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
        isDarkModeEnabled = json.safeOptBoolean("isDarkModeEnabled"),
        notificationAuthorization = json.safeOptString("notificationAuthorization").whenNotNull { DeviceContext.NotificationAuthorization.decodeJson(it) },
        operatingSystemName = json.safeOptString("operatingSystemName"),
        operatingSystemVersion = json.safeOptString("operatingSystemVersion"),
        pushEnvironment = json.safeOptString("pushEnvironment"),
        pushToken = json.optJSONObject("pushToken").whenNotNull { pushTokenJson ->
            PushToken.decodeJson(pushTokenJson, dateFormatting)
        },
        radio = json.safeOptString("radio"),
        screenWidth = json.safeOptInt("screenWidth"),
        screenHeight = json.safeOptInt("screenHeight"),
        sdkVersion = json.safeOptString("sdkVersion"),
        timeZone = json.safeOptString("timeZone"),
        isBluetoothEnabled = json.safeOptBoolean("isBluetoothEnabled"),
        userInfo = json.getJSONObject("userInfo").toAttributesHash(),
        isTestDevice = json.safeOptBoolean("isTestDevice"),
        location = json.optJSONObject("location").whenNotNull { locationJson ->
            Location.decodeJson(locationJson, dateFormatting)
        },
        advertisingIdentifier = json.safeOptString("advertisingIdentifier"),
        conversions = json.optJSONArray("conversions")?.getStringIterable()?.toList() ?: emptyList(),
        lastSeen = json.safeOptString("lastSeen")
    )
}

internal fun PushToken.Companion.decodeJson(json: JSONObject, dateFormatting: DateFormattingInterface): PushToken {
    return PushToken(
        json.safeGetString("value"),
        json.getDate("timestamp", dateFormatting)
    )
}

internal fun PushToken.encodeJson(dateFormatting: DateFormattingInterface): JSONObject {
    return JSONObject().apply {
        put("value", value)
        put("timestamp", dateFormatting.dateAsIso8601(timestamp))
    }
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
