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

package io.rover.sdk.advertising

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.core.streams.subscribeOn

class AdvertisingIdContentProvider(
    applicationContext: Context,
    ioScheduler: Scheduler,
    localStorage: LocalStorage
) : ContextProvider {
    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    private var advertisingId: String? = keyValueStorage[IDENTIFIER_KEY]
        set(token) {
            keyValueStorage[IDENTIFIER_KEY] = token
            field = token
        }

    init {
        Publishers.defer {
            advertisingId = try {
                AdvertisingIdClient.getAdvertisingIdInfo(applicationContext).id
            } catch (e: Exception) {
                log.e("Unable to retrieve advertising id: $e")
                null
            }
            log.v("Advertising id is now: $advertisingId")

            Publishers.just(Unit)
        }.subscribeOn(ioScheduler).subscribe { }
    }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            advertisingIdentifier = this.advertisingId
        )
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "advertising"
        private const val IDENTIFIER_KEY = "advertising-identifier"
    }
}
