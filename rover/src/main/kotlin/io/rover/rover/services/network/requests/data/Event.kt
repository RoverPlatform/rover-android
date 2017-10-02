package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Event
import io.rover.rover.platform.DateFormattingInterface
import io.rover.rover.services.network.putProp
import org.json.JSONObject

/**
 * Outgoing JSON DTO transformation for [Event]s, as submitted to the Rover GraphQL API.  This is
 * equivalent to the `EventInput` structure on the GraphQL API.
 */
fun Event.asJson(
    dateFormatting: DateFormattingInterface
): JSONObject {
    return JSONObject().apply {
        val props = listOf(
            Event::attributes,
            Event::name,
            Event::uuid
        )

        putProp(this@asJson, Event::timestamp, { dateFormatting.dateAsIso8601(it) })

        props.forEach { putProp(this@asJson, it) }
    }
}
