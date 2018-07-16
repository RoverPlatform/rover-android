package io.rover.core.data.graphql.operations

import io.rover.core.data.GraphQlRequest
import io.rover.core.data.domain.EventSnapshot
import io.rover.core.data.graphql.operations.data.asJson
import io.rover.core.data.graphql.safeGetString
import io.rover.core.platform.DateFormattingInterface
import org.json.JSONArray
import org.json.JSONObject

class SendEventsRequest(
    private val dateFormatting: DateFormattingInterface,
    events: List<EventSnapshot>
) : GraphQlRequest<String> {
    override val operationName: String = "TrackEvents"

    override val query: String = """
        mutation TrackEvents(${"\$"}events: [Event]!) {
            trackEvents(events:${"\$"}events)
        }
    """

    override val variables: JSONObject = JSONObject().apply {
        put("events", JSONArray(
            events.map { it.asJson(dateFormatting) }
        ))
    }

    override fun decodePayload(responseObject: JSONObject): String =
        responseObject.safeGetString("data")
}
