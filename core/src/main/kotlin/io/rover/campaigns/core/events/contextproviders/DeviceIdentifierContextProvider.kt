package io.rover.campaigns.core.events.contextproviders

import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider
import io.rover.campaigns.core.platform.DeviceIdentificationInterface

class DeviceIdentifierContextProvider(
    deviceIdentification: DeviceIdentificationInterface
) : ContextProvider {
    private val identifier = deviceIdentification.installationIdentifier
    private val deviceName = deviceIdentification.deviceName

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            deviceIdentifier = identifier,
            deviceName = deviceName
        )
    }
}
