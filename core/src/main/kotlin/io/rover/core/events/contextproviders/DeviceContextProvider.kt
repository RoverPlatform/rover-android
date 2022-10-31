package io.rover.core.events.contextproviders

import android.os.Build
import io.rover.core.data.domain.DeviceContext
import io.rover.core.events.ContextProvider
import io.rover.core.platform.getDeviceName

/**
 * Captures and adds details about the product details of the user's device and its running Android
 * version to [DeviceContext].
 */
class DeviceContextProvider : ContextProvider {
    private val deviceMarketingName = getDeviceName()
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            operatingSystemVersion = Build.VERSION.RELEASE,
            operatingSystemName = "Android",
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = deviceMarketingName
        )
    }
}
