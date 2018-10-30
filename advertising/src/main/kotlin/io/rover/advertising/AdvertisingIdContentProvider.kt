package io.rover.advertising

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import io.rover.core.data.domain.DeviceContext
import io.rover.core.events.ContextProvider
import io.rover.core.logging.log
import io.rover.core.platform.LocalStorage
import io.rover.core.streams.Publishers
import io.rover.core.streams.Scheduler
import io.rover.core.streams.subscribe
import io.rover.core.streams.subscribeOn

class AdvertisingIdContentProvider(
    applicationContext: Context,
    ioScheduler: Scheduler,
    localStorage: LocalStorage
) : ContextProvider {
    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    private var advertisingId: String? = keyValueStorage[IDENTIFIER_KEY]
        set(token) {
            keyValueStorage[IDENTIFIER_KEY] = token
            field = token
        }

    init {
        Publishers.defer {
            advertisingId = try {
                AdvertisingIdClient.getAdvertisingIdInfo(applicationContext).id
            } catch (e: Exception) {
                log.e("Unable to retrieve advertising id: $e")
                null
            }
            log.v("Advertising id is now: $advertisingId")

            Publishers.just(Unit)
        }.subscribeOn(ioScheduler).subscribe {  }
    }
    
    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            advertisingIdentifier = this.advertisingId
        )
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "io.rover.advertising"
        private const val IDENTIFIER_KEY = "advertising-identifier"
    }
}
