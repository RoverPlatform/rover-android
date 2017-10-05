package io.rover.rover.services.network

import io.rover.rover.core.domain.Context
import io.rover.rover.core.domain.Device
import io.rover.rover.core.domain.Event
import io.rover.rover.core.domain.Experience
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Wire Encoder is responsible for mapping and transforming the domain model objects
 * into their data-transfer JSON equivalents.
 */
interface WireEncoderInterface {
    fun decodeExperience(data: JSONObject): Experience

    fun decodeDevice(data: JSONObject): Device

    fun encodeEventsForSending(events: List<Event>): JSONArray

    fun encodeContextForSending(context: Context): JSONObject

    fun decodeErrors(errors: JSONArray): List<Exception>
}
