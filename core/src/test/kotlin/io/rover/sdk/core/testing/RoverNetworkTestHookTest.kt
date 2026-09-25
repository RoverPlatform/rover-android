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

package io.rover.sdk.core.testing

import android.content.Context
import android.content.pm.ApplicationInfo
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.data.config.EngageApiService
import io.rover.sdk.core.data.config.EngageHttpClient
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the [RoverNetworkTestHook] seam as consulted by the core Engage client, and pins the
 * storage-key surface the bench's cache priming depends on.
 *
 * The load-bearing property is the offline short-circuit: because the hook is installed as an
 * *application* interceptor, a stub answer means nothing reaches the network at all — asserted here
 * as `server.requestCount == 0` against a live [MockWebServer] that would happily have answered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoverNetworkTestHookTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    /**
     * Builds the Engage service — and with it the underlying `OkHttpClient`, which reads
     * [RoverNetworkTestHook.interceptor] as it is constructed. Deliberately not done in `setUp`: the
     * hook must be installed *before* the client is built, exactly as the bench installs it before
     * `Rover.initialize`.
     */
    private fun engageApiService(debuggable: Boolean = true): EngageApiService {
        val context = mock<Context>()
        val packageManager = mock<android.content.pm.PackageManager>()
        val packageInfo = mock<android.content.pm.PackageInfo>().apply { versionName = "1.0.0" }
        whenever(context.packageName).thenReturn("io.rover.test")
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(packageManager.getPackageInfo("io.rover.test", 0)).thenReturn(packageInfo)
        whenever(context.applicationInfo).thenReturn(applicationInfo(debuggable))

        val userInfo = mock<UserInfoInterface>()
        whenever(userInfo.reportedUserInfo).thenReturn(hashMapOf<String, Any>("userID" to "user-123"))

        val deviceIdentification = mock<DeviceIdentificationInterface>()
        whenever(deviceIdentification.installationIdentifier).thenReturn("device-abc")

        val httpClient = EngageHttpClient(
            context,
            FakeAuthenticationContext(enabledDomains = setOf(server.url("/").host)),
            userInfo,
            deviceIdentification
        )
        return EngageApiService.create(httpClient, server.url("/").toString())
    }

    @After
    fun tearDown() {
        // The hook is process-global: never let it leak into another test.
        RoverNetworkTestHook.interceptor = null
        server.shutdown()
    }

    /**
     * The one field [RoverNetworkTestHook.interceptorFor] reads. A real [ApplicationInfo] rather than a
     * mock, so the flag arithmetic under test is the platform's own.
     */
    private fun applicationInfo(debuggable: Boolean): ApplicationInfo =
        ApplicationInfo().apply {
            flags = if (debuggable) ApplicationInfo.FLAG_DEBUGGABLE else 0
        }

    /**
     * Records the request it observes and answers it without proceeding down the chain.
     */
    private class StubInterceptor(private val body: String) : Interceptor {
        var observed: Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            observed = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    @Test
    fun `with the hook null the Engage client reaches the network as before`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .addHeader("Content-Type", "application/json")
        )

        val response = engageApiService().getConfig()

        assertEquals(200, response.code())
        assertEquals(1, server.requestCount)
        assertEquals(
            "/config?deviceIdentifier=device-abc&userID=user-123",
            server.takeRequest().path
        )
    }

    @Test
    fun `Engage client short-circuits offline and the hook sees the decorated request`() = runTest {
        val stub = StubInterceptor("""{"hub":{"isEnabled":true}}""")
        RoverNetworkTestHook.interceptor = stub

        val response = engageApiService().getConfig()

        assertEquals(200, response.code())
        assertEquals("""{"hub":{"isEnabled":true}}""", response.body()!!.string())

        // Nothing left the device: the application interceptor ran before DNS and the connection.
        assertEquals(0, server.requestCount)

        // Added last, so all of the Engage client's own decoration is already applied.
        val observed = stub.observed!!
        assertEquals("/config", observed.url.encodedPath)
        assertEquals("device-abc", observed.url.queryParameter("deviceIdentifier"))
        assertEquals("user-123", observed.url.queryParameter("userID"))
        assertEquals("test-sdk-token", observed.header("x-rover-account-token"))
        assertEquals("Bearer id-token-123", observed.header("Authorization"))
        assertNotNull(observed.header("User-Agent"))
    }

    /**
     * The seam is gated on the HOST APP being debuggable, not on the annotation nobody enforces.
     *
     * This object ships in the published AAR and its `var` is reachable from any integrator's app, so
     * "nothing in the SDK assigns it" is a claim about our code, not a control on theirs. What makes a
     * release app safe is that the assignment has no effect there: the interceptor is never installed,
     * the request goes to the network as it always did, and neither the account token nor the fan
     * bearer header is handed to the caller. Asserted here through a real client build, because that
     * is the only place the guard can matter.
     */
    @Test
    fun `a non-debuggable host app does not get the hook`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .addHeader("Content-Type", "application/json")
        )
        val stub = StubInterceptor("""{"hub":{"isEnabled":true}}""")
        RoverNetworkTestHook.interceptor = stub

        val response = engageApiService(debuggable = false).getConfig()

        assertEquals(200, response.code())
        assertEquals("{}", response.body()!!.string())
        // The request went to the server, and the stub never saw it — so it never saw the
        // x-rover-account-token or Authorization headers either.
        assertEquals(1, server.requestCount)
        assertNull(stub.observed)
    }

    private class FakeAuthenticationContext(
        enabledDomains: Set<String>
    ) : AuthenticationContextInterface {
        override val sdkToken: String = "test-sdk-token"
        override var sdkAuthenticationEnabledDomains: Set<String> = enabledDomains
        override var sdkAuthenticationIdTokenRefreshCallback: () -> Unit = {}
        private var idToken: String? = "id-token-123"

        override fun setSdkAuthenticationIdToken(token: String?) {
            idToken = token
        }

        override fun clearSdkAuthenticationIdToken() {
            idToken = null
        }

        override suspend fun obtainSdkAuthenticationIdToken(checkValidity: Boolean): String? =
            idToken

        override fun enableSdkAuthIdTokenRefreshForDomain(pattern: String) {
            sdkAuthenticationEnabledDomains += pattern
        }
    }
}
