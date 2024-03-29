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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Settings.Secure.LOCATION_MODE
import android.provider.Settings.Secure.LOCATION_MODE_OFF
import androidx.core.content.ContextCompat
import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.privacy.PrivacyService

class LocationServicesContextProvider(
    private val applicationContext: android.content.Context,
    private val privacyService: PrivacyService
) : ContextProvider {
    companion object {
        private const val BACKGROUND_LOCATION_PERMISSION_CODE = "android.permission.ACCESS_BACKGROUND_LOCATION"
        private const val Q_VERSION_CODE = 29

        private const val AUTHORIZED_ALWAYS = "authorizedAlways"
        private const val AUTHORIZED_WHEN_IN_USE = "authorizedWhenInUse"
        private const val DENIED = "denied"
    }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) {
           return deviceContext.copy(locationAuthorization = DENIED, isLocationServicesEnabled = false)
        }

        val mode = Settings.Secure.getInt(applicationContext.contentResolver, LOCATION_MODE, LOCATION_MODE_OFF)
        val locationServicesEnabled = mode != LOCATION_MODE_OFF

        val fineLocationGranted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundLocationGranted = ContextCompat.checkSelfPermission(applicationContext, BACKGROUND_LOCATION_PERMISSION_CODE) == PackageManager.PERMISSION_GRANTED

        val locationAuthorization = when {
            fineLocationGranted && (Build.VERSION.SDK_INT < Q_VERSION_CODE || backgroundLocationGranted) -> AUTHORIZED_ALWAYS
            fineLocationGranted && Build.VERSION.SDK_INT >= Q_VERSION_CODE && !backgroundLocationGranted -> AUTHORIZED_WHEN_IN_USE
            else -> DENIED
        }

        fineLocationGranted && (Build.VERSION.SDK_INT < Q_VERSION_CODE || backgroundLocationGranted)

        return deviceContext.copy(locationAuthorization = locationAuthorization, isLocationServicesEnabled = locationServicesEnabled)
    }
}
