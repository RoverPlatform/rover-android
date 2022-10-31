package io.rover.campaigns.core.events.contextproviders

import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider
import io.rover.campaigns.core.events.PushTokenTransmissionChannel
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.LocalStorage
import io.rover.campaigns.core.platform.whenNotNull
import java.util.Date

/**
 * Captures and adds the Firebase push token to [DeviceContext].  As a [PushTokenTransmissionChannel], it
 * expects to be informed of any changes to the push token.
 */
class FirebasePushTokenContextProvider(
    localStorage: LocalStorage
) : ContextProvider, PushTokenTransmissionChannel {
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(pushToken = token.whenNotNull {
            DeviceContext.PushToken(
                it,
                timestampAsNeeded()
            )
        })
    }

    override fun setPushToken(token: String?) {
        if (this.token != token) {
            this.token = token
            this.timestamp = null
            timestampAsNeeded()
            val elapsed = (Date().time - launchTime.time) / 1000
            log.v("A new push token set after $elapsed seconds: $token")
        } else {
            log.v("Push token update received, token not changed.")
        }
    }

    private val launchTime = Date()
    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    private var token: String?
        get() = keyValueStorage[TOKEN_KEY]
        set(token) { keyValueStorage[TOKEN_KEY] = token }

    private var timestamp: String?
        get() = keyValueStorage[TIMESTAMP_KEY]
        set(token) { keyValueStorage[TIMESTAMP_KEY] = token }

    private fun timestampAsNeeded(): Date {
        // retrieves the current timestamp value, setting it to now if it's missing (say, if running
        // on an early 2.0 beta install where timestamp was not set).

        if (timestamp == null) {
            timestamp = (System.currentTimeMillis() / 1000L).toString()
        }

        return Date(timestamp!!.toLong() * 1000)
    }

    init {
        if (token == null) {
            log.i("No push token is set yet.")
        } else {
            log.i("Push token already set: $token")
        }
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "fcm-push-context-provider"
        private const val TOKEN_KEY = "push-token"
        private const val TIMESTAMP_KEY = "timestamp"
    }
}
