package io.rover.location.events.contextproviders

import io.rover.core.data.domain.DeviceContext
import io.rover.core.events.ContextProvider
import io.rover.core.streams.subscribe
import io.rover.location.GoogleBackgroundLocationServiceInterface
import io.rover.core.data.domain.Location

class LocationContextProvider(
    googleBackgroundLocationService: GoogleBackgroundLocationServiceInterface
): ContextProvider {
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
