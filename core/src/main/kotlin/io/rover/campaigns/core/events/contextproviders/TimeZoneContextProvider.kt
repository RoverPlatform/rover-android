package io.rover.campaigns.core.events.contextproviders

import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider
import java.util.TimeZone

/**
 * Captures and adds the device's time zone to a [DeviceContext].
 */
class TimeZoneContextProvider : ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            // Time zone name in Paul Eggert zoneinfo "America/Montreal" format.
            timeZone = TimeZone.getDefault().id
        )
    }
}