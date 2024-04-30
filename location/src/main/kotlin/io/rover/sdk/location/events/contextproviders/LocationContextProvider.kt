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

package io.rover.sdk.location.events.contextproviders

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.data.domain.Location
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.platform.round
import io.rover.sdk.core.privacy.PrivacyService
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.location.GoogleBackgroundLocationServiceInterface

class LocationContextProvider(
        googleBackgroundLocationService: GoogleBackgroundLocationServiceInterface,
        privacyService: PrivacyService
) : ContextProvider, PrivacyService.TrackingEnabledChangedListener {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        val roundedLocation = currentLocation?.let { location ->
            return@let location.copy(
                    coordinate = Location.Coordinate(
                            latitude = location.coordinate.latitude.round(2),
                            longitude = location.coordinate.longitude.round(2)
                    )
            )
        }

        return deviceContext.copy(
            location = roundedLocation
        )
    }

    private var currentLocation: Location? = null

    init {
        googleBackgroundLocationService.locationUpdates.subscribe { location ->
            currentLocation = location
        }

        privacyService.registerTrackingEnabledChangedListener(this)
    }

    override fun onTrackingModeChanged(trackingMode: PrivacyService.TrackingMode) {
        if (trackingMode == PrivacyService.TrackingMode.Anonymized) {
            currentLocation = null
        }
    }
}
