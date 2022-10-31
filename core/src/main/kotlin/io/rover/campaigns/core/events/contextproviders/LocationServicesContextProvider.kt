package io.rover.campaigns.core.events.contextproviders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider
import android.provider.Settings.Secure.LOCATION_MODE_OFF
import android.provider.Settings.Secure.LOCATION_MODE
import android.provider.Settings
import androidx.core.content.ContextCompat

class LocationServicesContextProvider(val applicationContext: android.content.Context) : ContextProvider {
    companion object {
        private const val BACKGROUND_LOCATION_PERMISSION_CODE = "android.permission.ACCESS_BACKGROUND_LOCATION"
        private const val Q_VERSION_CODE = 29

        private const val AUTHORIZED_ALWAYS = "authorizedAlways"
        private const val AUTHORIZED_WHEN_IN_USE = "authorizedWhenInUse"
        private const val DENIED = "denied"
    }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
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

        return  deviceContext.copy(locationAuthorization = locationAuthorization, isLocationServicesEnabled = locationServicesEnabled)
    }
}
