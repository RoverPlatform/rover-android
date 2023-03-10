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
import io.rover.sdk.core.data.domain.EventSnapshot
import io.rover.sdk.core.data.graphql.getDate
import io.rover.sdk.core.data.graphql.putProp
import io.rover.sdk.core.data.graphql.safeGetString
import io.rover.sdk.core.data.graphql.safeOptString
import io.rover.sdk.core.platform.DateFormattingInterface
import org.json.JSONObject
import java.util.UUID

/**
 * Outgoing JSON DTO transformation for [EventSnapshot]s, as submitted to the Rover GraphQL API.
 * This is equivalent to the `EventInput` structure on the GraphQL API.
 */
internal fun EventSnapshot.asJson(
    dateFormatting: DateFormattingInterface
): JSONObject {
    return JSONObject().apply {
        val props = listOf(
            EventSnapshot::name,
            EventSnapshot::id,
            EventSnapshot::namespace
        )

        putProp(this@asJson, EventSnapshot::timestamp, { dateFormatting.dateAsIso8601(it, true) })

        props.forEach { putProp(this@asJson, it) }

        putProp(this@asJson, EventSnapshot::attributes) { it.encodeJson() }

        putProp(this@asJson, EventSnapshot::deviceContext, "device") { it.asJson(dateFormatting) }
    }
}

internal fun EventSnapshot.Companion.decodeJson(json: JSONObject, dateFormatting: DateFormattingInterface): EventSnapshot {
    return EventSnapshot(
        attributes = json.getJSONObject("attributes").toAttributesHash(),
        name = json.safeGetString("name"),
        timestamp = json.getDate("timestamp", dateFormatting, true),
        id = UUID.fromString(json.safeGetString("id")),
        deviceContext = DeviceContext.decodeJson(json.getJSONObject("device"), dateFormatting),
        namespace = json.safeOptString("namespace")
    )
}
