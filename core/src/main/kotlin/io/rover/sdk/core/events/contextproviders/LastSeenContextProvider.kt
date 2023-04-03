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

package io.rover.sdk.core.events.contextproviders

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.AppLastSeenInterface
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.platform.LocalStorage
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class LastSeenContextProvider(
        localStorage: LocalStorage
) : ContextProvider, AppLastSeenInterface {
    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    //This is an ISO 8601 date string
    private var lastSeen: String?
        get() {
            keyValueStorage[IDENTIFIER_KEY]?.let {
                return it
            } ?: return null
        }

        set(timestamp) {
            if (timestamp == null) {
                keyValueStorage[IDENTIFIER_KEY] = null
            } else {
                keyValueStorage[IDENTIFIER_KEY] = timestamp
            }
        }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
                lastSeen = this.lastSeen
        )
    }

    override fun markAppLastSeen() {
        val current = OffsetDateTime.of(LocalDateTime.now(), OffsetDateTime.now().offset)
        this.lastSeen = current.format(DateTimeFormatter.ISO_DATE_TIME)
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "appLastSeen"
        private const val IDENTIFIER_KEY = "appLastSeenTimestamp"
    }
}