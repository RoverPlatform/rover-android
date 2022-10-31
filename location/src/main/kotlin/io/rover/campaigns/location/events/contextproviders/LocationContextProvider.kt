package io.rover.campaigns.location.events.contextproviders

import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.data.domain.Location
import io.rover.campaigns.core.events.ContextProvider
import io.rover.campaigns.core.streams.subscribe
import io.rover.campaigns.location.GoogleBackgroundLocationServiceInterface

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
