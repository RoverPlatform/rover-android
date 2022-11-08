package io.rover.sdk.location.events.contextproviders

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.data.domain.Location
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.location.GoogleBackgroundLocationServiceInterface

class LocationContextProvider(
    googleBackgroundLocationService: GoogleBackgroundLocationServiceInterface
) : ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            location = currentLocation
        )
    }

    private var currentLocation: Location? = null

    init {
        googleBackgroundLocationService.locationUpdates.subscribe { location ->
            currentLocation = location
        }
    }
}
