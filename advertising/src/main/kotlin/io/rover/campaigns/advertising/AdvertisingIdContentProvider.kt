package io.rover.campaigns.advertising

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.LocalStorage
import io.rover.campaigns.core.streams.Publishers
import io.rover.campaigns.core.streams.Scheduler
import io.rover.campaigns.core.streams.subscribe
import io.rover.campaigns.core.streams.subscribeOn

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
        }.subscribeOn(ioScheduler).subscribe { }
    }

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            advertisingIdentifier = this.advertisingId
        )
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "advertising"
        private const val IDENTIFIER_KEY = "advertising-identifier"
    }
}
