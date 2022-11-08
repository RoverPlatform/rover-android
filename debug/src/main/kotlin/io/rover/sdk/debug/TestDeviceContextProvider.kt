package io.rover.sdk.debug

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider

class TestDeviceContextProvider(
    private val debugPreferences: DebugPreferences
) : ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        // have to re-interrogate the shared preferences every time, but it should be quite fast.
        return deviceContext.copy(
            isTestDevice = debugPreferences.currentTestDeviceState()
        )
    }
}
