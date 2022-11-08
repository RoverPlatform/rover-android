package io.rover.sdk.core.events.contextproviders

import io.rover.core.BuildConfig
import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider

/**
 * Captures and adds the Rover SDK version number to [DeviceContext].
 */
class SdkVersionContextProvider : ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            // cloud-side campaigns services have issues with meta/prerelease version strings.
            // Drop it.  https://github.com/RoverPlatform/rover/issues/2481
            sdkVersion = BuildConfig.ROVER_SDK_VERSION.substringBefore("-")
        )
    }
}
