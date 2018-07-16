package io.rover.core.events.contextproviders

import android.os.Handler
import io.rover.core.data.domain.AttributeValue
import io.rover.core.logging.log
import io.rover.core.platform.LocalStorage
import io.rover.core.data.domain.DeviceContext
import io.rover.core.events.ContextProvider
import io.rover.core.events.EventQueueService
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.PushTokenTransmissionChannel
import io.rover.core.events.domain.Event
import io.rover.core.platform.whenNotNull
import java.util.Date
import java.util.concurrent.Executors

/**
 * Captures and adds the Firebase push token to [DeviceContext].  As a [PushTokenTransmissionChannel], it
 * expects to be informed of any changes to the push token.
 *
 * TODO: the requirement of the push token reset closure is very awkward; it's technically a push
 * concern, not an Events concern.  Perhaps the push plugin should contribute this provider.
 */
class FirebasePushTokenContextProvider(
    localStorage: LocalStorage,
    private val resetPushToken: () -> Unit
): ContextProvider, PushTokenTransmissionChannel {

    override fun registeredWithEventQueue(eventQueue: EventQueueServiceInterface) {
        this.eventQueue = eventQueue
    }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            pushToken = token
        )
    }

    override fun setPushToken(token: String?) {
        if(this.token != token) {
            val event = Event(
                when {
                    this.token == null -> "Push Token Added"
                    token == null -> "Push Token Removed"
                    else -> "Push Token Updated"
                },
                listOfNotNull(
                    token.whenNotNull { token -> Pair("currentToken", AttributeValue.String(token)) },
                    this.token.whenNotNull { previousToken -> Pair("previousToken", AttributeValue.String(previousToken)) }
                ).associate { it }
            )
            this.token = token
            val eventsPlugin = eventQueue ?: throw RuntimeException("registeredWithEventQueue() not called on FirebasePushTokenContextProvider during setup.")
            eventsPlugin.trackEvent(event, EventQueueService.ROVER_NAMESPACE)
            val elapsed = (Date().time - launchTime.time) / 1000
            log.v("Push token set after $elapsed seconds.")
        }
    }

    private val launchTime = Date()
    private var eventQueue: EventQueueServiceInterface? = null
    private val keyValueStorage = localStorage.getKeyValueStorageFor(Companion.STORAGE_CONTEXT_IDENTIFIER)

    private var token: String?
        get() = keyValueStorage[Companion.TOKEN_KEY]
        set(token) { keyValueStorage[Companion.TOKEN_KEY] = token }

    init {
        if (token == null) {
            log.e("No push token is set yet.")
            Handler().postDelayed({
                if(token == null) {
                    // token still null? then attempt a reset. This case can happen if the FCM token
                    // was already set and received before the Rover SDK 2.x was integrated, meaning
                    // that FCM believes that the app knows what the push token is, but at least the
                    // Rover SDK itself does not.

                    log.w("Push token is still not set. Perhaps token was received before Rover SDK was integrated. Forcing reset.")
                    Executors.newSingleThreadExecutor().execute {
                        resetPushToken()
                    }
                }
            }, TOKEN_RESET_TIMEOUT)
        }
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "io.rover.rover.fcm-push-context-provider"
        private const val TOKEN_KEY = "push-token"
        private const val TOKEN_RESET_TIMEOUT = 4 * 1000L
    }
}
