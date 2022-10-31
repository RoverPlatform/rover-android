package io.rover.campaigns.core.events.contextproviders

import io.rover.campaigns.core.BuildConfig
import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider

/**
 * Captures and adds the Rover SDK version number to [DeviceContext].
 */
class SdkVersionContextProvider : ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            // cloud-side campaigns services have issues with meta/prerelease version strings.
            // Drop it.  https://github.com/RoverPlatform/rover/issues/2481
            sdkVersion = BuildConfig.ROVER_CAMPAIGNS_VERSION.substringBefore("-")
        )
    }
}
