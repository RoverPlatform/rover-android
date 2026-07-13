package io.rover.sdk.core.data

import io.rover.sdk.core.platform.KeyValueStorage
import io.rover.sdk.core.platform.LocalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OkHttpAuthenticationTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `authenticate omits bearer token and skips refresh when no token is cached`() = runTest {
        val storage = InMemoryKeyValueStorage()
        val authenticationContext = AuthenticationContext(
            sdkToken = "sdk-token",
            localStorage = SingleStorageLocalStorage(storage)
        )
        authenticationContext.enableSdkAuthIdTokenRefreshForDomain("andrew-rover-laptop")

        var refreshRequests = 0
        authenticationContext.sdkAuthenticationIdTokenRefreshCallback = {
            refreshRequests += 1
        }

        val request = Request.Builder()
            .url("http://andrew-rover-laptop/engage/conversations")
            .build()

        val authenticated = OkHttpRequestAuthenticator.authenticate(authenticationContext, request)
        assertNull(authenticated.header("Authorization"))
        assertNull(storage["sdkAuthenticationToken"])
        assertEquals(0, refreshRequests)
    }

    @Test
    fun `authenticate refreshes expiring cached token and attaches bearer token`() = runTest {
        val storage = InMemoryKeyValueStorage()
        val authenticationContext = AuthenticationContext(
            sdkToken = "sdk-token",
            localStorage = SingleStorageLocalStorage(storage)
        )
        authenticationContext.enableSdkAuthIdTokenRefreshForDomain("andrew-rover-laptop")

        storage["sdkAuthenticationToken"] = jwtWithExpiry(Date().time / 1000 + 30)

        val freshToken = jwtWithExpiry(4_102_444_800L)
        var refreshRequests = 0
        authenticationContext.sdkAuthenticationIdTokenRefreshCallback = {
            refreshRequests += 1
            authenticationContext.setSdkAuthenticationIdToken(freshToken)
        }

        val request = Request.Builder()
            .url("http://andrew-rover-laptop/engage/conversations")
            .build()

        val authenticated = OkHttpRequestAuthenticator.authenticate(authenticationContext, request)

        assertEquals(1, refreshRequests)
        assertEquals("Bearer $freshToken", authenticated.header("Authorization"))
        assertEquals(freshToken, storage["sdkAuthenticationToken"])
    }

    @Test
    fun `authenticate waits for asynchronously refreshed token when cached token is expiring soon`() = runTest {
        val storage = InMemoryKeyValueStorage()
        val authenticationContext = AuthenticationContext(
            sdkToken = "sdk-token",
            localStorage = SingleStorageLocalStorage(storage)
        )
        authenticationContext.enableSdkAuthIdTokenRefreshForDomain("andrew-rover-laptop")

        storage["sdkAuthenticationToken"] = jwtWithExpiry(Date().time / 1000 + 30)

        val freshToken = jwtWithExpiry(4_102_444_800L)
        val refreshCompleted = CountDownLatch(1)
        var refreshRequests = 0
        var refreshThread: Thread? = null
        authenticationContext.sdkAuthenticationIdTokenRefreshCallback = {
            refreshRequests += 1
            refreshThread = Thread {
                Thread.sleep(75)
                authenticationContext.setSdkAuthenticationIdToken(freshToken)
                refreshCompleted.countDown()
            }.apply { start() }
        }

        val request = Request.Builder()
            .url("http://andrew-rover-laptop/engage/conversations")
            .build()

        val startedAt = System.nanoTime()
        val authenticated = OkHttpRequestAuthenticator.authenticate(authenticationContext, request)
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        refreshThread?.join(1_000)

        assertEquals(1, refreshRequests)
        assertTrue(refreshCompleted.await(0, TimeUnit.MILLISECONDS))
        assertTrue("authenticate should wait for the async refresh to finish", elapsedMillis >= 50)
        assertEquals("Bearer $freshToken", authenticated.header("Authorization"))
        assertEquals(freshToken, storage["sdkAuthenticationToken"])
    }

    @Test
    fun `authenticate reuses malformed cached token without refresh`() = runTest {
        val storage = InMemoryKeyValueStorage()
        val authenticationContext = AuthenticationContext(
            sdkToken = "sdk-token",
            localStorage = SingleStorageLocalStorage(storage)
        )
        authenticationContext.enableSdkAuthIdTokenRefreshForDomain("andrew-rover-laptop")

        val malformedToken = "not-a-jwt"
        storage["sdkAuthenticationToken"] = malformedToken

        var refreshRequests = 0
        authenticationContext.sdkAuthenticationIdTokenRefreshCallback = {
            refreshRequests += 1
        }

        val request = Request.Builder()
            .url("http://andrew-rover-laptop/engage/conversations")
            .build()

        val authenticated = OkHttpRequestAuthenticator.authenticate(authenticationContext, request)

        assertEquals(0, refreshRequests)
        assertEquals("Bearer $malformedToken", authenticated.header("Authorization"))
        assertEquals(malformedToken, storage["sdkAuthenticationToken"])
    }

    private fun jwtWithExpiry(expiryEpochSeconds: Long): String {
        val header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{}".toByteArray())
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"exp\":$expiryEpochSeconds}".toByteArray())
        return "$header.$payload.signature"
    }

    private class SingleStorageLocalStorage(
        private val storage: KeyValueStorage
    ) : LocalStorage {
        override fun getKeyValueStorageFor(namedContext: String): KeyValueStorage = storage
    }

    private class InMemoryKeyValueStorage : KeyValueStorage {
        private val values = linkedMapOf<String, String>()

        override fun get(key: String): String? = values[key]

        override fun set(key: String, value: String?) {
            if (value == null) {
                values.remove(key)
            } else {
                values[key] = value
            }
        }

        override fun unset(key: String) {
            values.remove(key)
        }

        override val keys: Set<String>
            get() = values.keys
    }
}
