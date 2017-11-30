package io.rover.rover.platform

import io.rover.rover.core.domain.DeviceState
import io.rover.rover.core.domain.Event
import io.rover.rover.core.domain.Experience
import io.rover.rover.services.network.WireEncoder
import io.rover.rover.services.network.requests.data.encodeJson
import org.json.JSONObject

// Android's build system (or perhaps the Kotlin compiler) recently changed to apparently not allow
// test code to reference types from the Android SDK (even if it is done so only statically or the
// code itself is classpath shadowed).
//
// Thus we here expose a bit of functionality to allow Kotlin test code to get the useful bits out
// of a few types.

fun WireEncoder.encodeEventsToStringJsonForTests(events: List<Event>): String =
    this.encodeEventsForSending(events).toString(4)

fun WireEncoder.decodeDeviceStateFromJsonStringForTests(json: String): DeviceState =
    this.decodeDeviceState(JSONObject(json))

fun DeviceState.encodeJsonToStringForTests(): String = this.encodeJson().toString(4)

fun Experience.encodeJsonToStringForTests(): String = this.encodeJson().toString(4)


fun WireEncoder.decodeExperienceFromStringForTests(json: String): Experience =
    this.decodeExperience(JSONObject(json))
