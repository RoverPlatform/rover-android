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

package io.rover.sdk.core.data.graphql.operations

import io.rover.sdk.core.data.GraphQlRequest
import io.rover.sdk.core.data.domain.EventSnapshot
import io.rover.sdk.core.data.graphql.operations.data.asJson
import io.rover.sdk.core.data.graphql.safeGetString
import io.rover.sdk.core.platform.DateFormattingInterface
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
        put(
            "events",
            JSONArray(
                events.map { it.asJson(dateFormatting) }
            )
        )
    }

    override fun decodePayload(responseObject: JSONObject): String =
        responseObject.safeGetString("data")
}
