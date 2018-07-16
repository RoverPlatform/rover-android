package io.rover.core.events.contextproviders

import io.rover.core.data.domain.DeviceContext
import io.rover.core.events.ContextProvider
import io.rover.core.events.DeviceAttributesInterface
import io.rover.core.events.domain.Event

/**
 * Allows you to include arbitrary attributes (name/value pairs) within the [DeviceContext] sent
 * alongside [Event]s.
 *
 * See [DeviceAttributesInterface.update].
 */
class DeviceAttributesContextProvider(
    private val deviceAttributes: DeviceAttributesInterface
): ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            attributes = deviceAttributes.currentAttributes
        )
    }
}
