package io.rover.campaigns.notifications

import androidx.core.app.NotificationManagerCompat
import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider

/**
 * Will identify if the user has disabled notifications from the app.
 *
 * Note that on Android they are enabled by default.
 */
class NotificationContextProvider(
    private val applicationContext: android.content.Context
) : ContextProvider {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        val notificationManager = NotificationManagerCompat.from(applicationContext)

        return deviceContext.copy(
            notificationAuthorization = when (notificationManager.areNotificationsEnabled()) {
                true -> DeviceContext.NotificationAuthorization.Authorized
                false -> DeviceContext.NotificationAuthorization.Denied
            }
        )
    }
}