package io.rover.core.events

import io.rover.core.data.domain.Attributes
import io.rover.core.data.graphql.operations.data.encodeJson
import io.rover.core.data.graphql.operations.data.toFlatAttributesHash
import io.rover.core.events.domain.Event
import io.rover.core.logging.log
import io.rover.core.platform.LocalStorage
import org.json.JSONObject

class DeviceAttributes(
    localStorage: LocalStorage,
    private val eventQueueService: EventQueueServiceInterface
): DeviceAttributesInterface {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)
    override fun update(builder: (attributes: Attributes) -> Unit) {
        currentAttributes.apply {
            builder(this)
            currentAttributes = this
        }

        // emit an event.  Note that we do not include the device attributes directly as an event:
        // instead the DeviceAttributesContextProvider will include them for us.
        eventQueueService.trackEvent(
            Event(
                "Device Attributes Updated",
                hashMapOf()
            )
        )
    }

    override fun clear() {
        currentAttributes = hashMapOf()
    }

    override var currentAttributes: Attributes = try {
        val currentAttributesJson = store[ATTRIBUTES_KEY]
        when(currentAttributesJson) {
            null -> hashMapOf()
            else -> JSONObject(store[ATTRIBUTES_KEY]).toFlatAttributesHash()
        }
    } catch(throwable: Throwable) {
        log.w("Corrupted local device attributes, ignoring and starting fresh.  Cause: ${throwable.message}")
        hashMapOf()
    }
        private set(value) {
            field = value
            store[ATTRIBUTES_KEY] = value.encodeJson().toString()
            log.v("Stored new device attributes.")
        }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "io.rover.core.device-attributes"
        private const val ATTRIBUTES_KEY = "attributes"
    }
}
