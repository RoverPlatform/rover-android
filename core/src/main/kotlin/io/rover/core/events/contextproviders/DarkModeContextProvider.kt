package io.rover.core.events.contextproviders

import android.app.Application
import android.content.res.Configuration
import io.rover.core.data.domain.DeviceContext
import io.rover.core.events.ContextProvider

class DarkModeContextProvider(val application: Application) : ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        val isInDarkMode = (application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

        return deviceContext.copy(isDarkModeEnabled = isInDarkMode)
    }
}