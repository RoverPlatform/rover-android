package io.rover.core.events.contextproviders

import android.content.res.Resources
import io.rover.core.data.domain.DeviceContext
import io.rover.core.events.ContextProvider
import kotlin.math.roundToInt

/**
 * Captures and adds the screen geometry (as dps, not pixels) to a [DeviceContext].
 */
class ScreenContextProvider(
    private val resources: Resources
) : ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        val metrics = resources.displayMetrics
        // note that this includes *all* screen space, including the status bar and navigation bar.
        return deviceContext.copy(
            screenWidth = (metrics.widthPixels / metrics.density).roundToInt(),
            screenHeight = (metrics.heightPixels / metrics.density).roundToInt()
        )
    }
}
