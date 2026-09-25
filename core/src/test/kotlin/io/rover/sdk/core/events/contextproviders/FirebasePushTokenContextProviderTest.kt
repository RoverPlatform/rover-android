package io.rover.sdk.core.events.contextproviders

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.platform.KeyValueStorage
import io.rover.sdk.core.platform.LocalStorage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
class FirebasePushTokenContextProviderTest {
    @Test
    fun `setPushToken survives timestamp being cleared after it is written`() {
        val storage = InterleavingLocalStorage()
        val provider = FirebasePushTokenContextProvider(storage)
        storage.clearTimestampAfterNextWrite = true

        provider.setPushToken("token")

        val pushToken = provider.captureContext(DeviceContext.blank()).pushToken
        assertThat(pushToken?.value, equalTo("token"))
    }

    private class InterleavingLocalStorage : LocalStorage {
        private val values = ConcurrentHashMap<String, String>()
        private val storage = object : KeyValueStorage {
            override fun get(key: String): String? {
                if (key == TIMESTAMP_KEY && clearTimestampOnNextRead) {
                    clearTimestampOnNextRead = false
                    values.remove(TIMESTAMP_KEY)
                    return null
                }
                return values[key]
            }

            override fun set(key: String, value: String?) {
                if (value == null) values.remove(key) else values[key] = value
                if (key == TIMESTAMP_KEY && value != null && clearTimestampAfterNextWrite) {
                    clearTimestampAfterNextWrite = false
                    clearTimestampOnNextRead = true
                }
            }

            override fun unset(key: String) {
                values.remove(key)
            }

            override val keys: Set<String>
                get() = values.keys
        }

        var clearTimestampAfterNextWrite = false
        private var clearTimestampOnNextRead = false

        override fun getKeyValueStorageFor(namedContext: String): KeyValueStorage = storage

        companion object {
            private const val TIMESTAMP_KEY = "timestamp"
        }
    }
}
