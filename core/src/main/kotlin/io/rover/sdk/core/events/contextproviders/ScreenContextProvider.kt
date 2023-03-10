/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.core.events.contextproviders

import android.content.res.Resources
import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider
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
