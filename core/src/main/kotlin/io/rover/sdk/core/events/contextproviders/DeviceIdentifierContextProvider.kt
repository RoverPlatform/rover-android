package io.rover.sdk.core.events.contextproviders

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.platform.DeviceIdentificationInterface

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
