package io.rover.rover.services.network

import io.rover.rover.core.domain.Context
import io.rover.rover.core.domain.Event
import io.rover.rover.core.domain.Experience
import io.rover.rover.platform.DateFormattingInterface
import io.rover.rover.services.network.requests.data.asJson
import io.rover.rover.services.network.requests.data.decodeJson
import io.rover.rover.services.network.requests.data.getStringIterable
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KProperty1



/**
 * Responsible for marshalling Data Transfer objects to and from
 * their appropriate wire-format representation expected by the Rover API.
 */
class WireEncoder(
    private val dateFormatting: DateFormattingInterface
): WireEncoderInterface {
    override fun encodeContextForSending(context: Context): JSONObject {
        return context
            .asJson()
    }

    fun decodeContext(data: String): Context {
        val json = JSONObject(data)
        return Context.Companion.decodeJson(json)
    }

    /**
     * Encode a list of events for submission to the cloud-side API.
     */
    override fun encodeEventsForSending(events: List<Event>): JSONArray =
        JSONArray(
            events.map { it.asJson(dateFormatting) }
        )

    override fun decodeExperience(data: JSONObject): Experience = Experience.decodeJson(data)

    override fun decodeErrors(errors: JSONArray): List<Exception> {
        return errors.getStringIterable().map {
            Exception(it)
        }
    }
}

fun <T, R> JSONObject.putProp(obj: T, prop: KProperty1<T, R>, transform: ((R) -> Any)? = null) {
    put(
        prop.name,
        if(transform != null) transform(prop.get(obj)) else prop.get(obj)
    )
}
