package io.rover.rover.services.network.requests

import io.rover.rover.core.domain.Context
import io.rover.rover.core.domain.Event
import io.rover.rover.services.network.NetworkRequest
import io.rover.rover.services.network.WireEncoder
import io.rover.rover.services.network.WireEncoderInterface
import org.json.JSONObject

class SendEventsRequest(
    events: List<Event>,
    context: Context,
    wireEncoder: WireEncoderInterface
) : NetworkRequest<String> {
    override val operationName: String = "SendEvents"

    override val query: String = """
        SendEvents(${"\$"}events: [Event]!, ${"\$"}context: Context) {
            sendEvents(events: ${"\$"}events, context: ${"\$"}context)
        }
    """

    override val variables: JSONObject = JSONObject().apply {
        put("events", wireEncoder.encodeEventsForSending(events))
        put("context", wireEncoder.encodeContextForSending(context))
    }

    override fun decodePayload(responseObject: JSONObject, wireEncoder: WireEncoderInterface): String =
        responseObject.getString("data")
}
