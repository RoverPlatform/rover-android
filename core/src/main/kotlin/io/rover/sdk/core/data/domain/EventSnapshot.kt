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

package io.rover.sdk.core.data.domain

import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.graphql.operations.data.asJson
import io.rover.sdk.core.data.graphql.operations.data.encodeJson
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.platform.DateFormattingInterface
import java.util.Date
import java.util.UUID

/**
 * An Event as it will be sent to the Rover API.
 *
 * Exactly equivalent to [Event] but also includes some metadata, namely the [DeviceContext] and the
 * namespace.
 */
data class EventSnapshot(
    val name: String,
    val attributes: Attributes,
    val timestamp: Date,
    val id: UUID,
    val namespace: String?,
    val deviceContext: DeviceContext
) {
    companion object {
        /**
         * Add a few elements of metadata to an [Event] to create an [EventSnapshot]
         *
         * @param namespace Specify a namespace name to your events in order to separate them from
         * other events.  They will appear as a separate table in your BigQuery instance. May be
         * left null to have it appear in a default table.
         */
        fun fromEvent(event: Event, deviceContext: DeviceContext, namespace: String?): EventSnapshot {
            return EventSnapshot(
                event.name,
                event.attributes,
                event.timestamp,
                event.id,
                namespace,
                deviceContext
            )
        }
    }

    var debugAttributesDescription: String = this.attributes.encodeJson().toString(4)

    var debugDeviceContextDescription: String = this.deviceContext.asJson(Rover.shared.resolve(DateFormattingInterface::class.java)!!).toString(4)
}
