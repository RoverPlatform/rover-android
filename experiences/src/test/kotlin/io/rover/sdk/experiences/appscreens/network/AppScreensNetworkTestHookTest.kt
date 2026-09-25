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

package io.rover.sdk.experiences.appscreens.network

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.core.testing.RoverNetworkTestHook
import io.rover.sdk.experiences.appscreens.AppScreenDataScope
import kotlinx.coroutines.runBlocking
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Covers the [RoverNetworkTestHook] seam as consulted by the two App Screens clients.
 *
 * The load-bearing property is the offline short-circuit: because the hook is installed as an
 * *application* interceptor, a stub answer means nothing reaches the network at all — asserted here
 * as `server.requestCount == 0` against a live [MockWebServer] that would happily have answered.
 * The other half is decoration: being added last, the hook sees the request exactly as the SDK would
 * have put it on the wire.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppScreensNetworkTestHookTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.cacheDir, "rover-appscreens-http").deleteRecursively()

        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        // The hook is process-global: never let it leak into another test.
        RoverNetworkTestHook.interceptor = null
        server.shutdown()
    }

    private fun url(path: String): Uri = Uri.parse(server.url(path).toString())

    private fun documentClient(): AppScreensDocumentClient =
        AppScreensDocumentClient(ApplicationProvider.getApplicationContext())

    private fun dataClient(): AppScreensDataClient = AppScreensDataClient(
        context = ApplicationProvider.getApplicationContext(),
        authenticationContext = FakeAuthenticationContext(
            enabledDomains = setOf(server.hostName),
            token = "FAKE_JWT"
        ),
        userInfo = FakeUserInfo("user-123"),
        deviceIdentification = FakeDeviceIdentification("device-abc")
    )

    /**
     * Records the request it observes and answers it without proceeding down the chain.
     */
    private class StubInterceptor(
        private val body: String,
        private val scope: String = "personalized"
    ) : Interceptor {
        var observed: Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            observed = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("ETag", "\"stub-v1\"")
                .header(AppScreenDataScope.HEADER_NAME, scope)
                .body(body.toResponseBody("text/html".toMediaType()))
                .build()
        }
    }

    @Test
    fun `with the hook null the document client reaches the network as before`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"fx-home-v4\"")
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("LIVE")
        )

        val document = documentClient().fetchDocument(url("/a/home"))

        assertEquals("LIVE", document.html)
        assertEquals("\"fx-home-v4\"", document.etag)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `with the hook null the data client reaches the network as before`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("""{"templateHash":"live-h1"}""")
        )

        val envelope = dataClient().fetchScreenData(url("/a/home"), AppScreenDataScope.PUBLIC)

        assertEquals("live-h1", envelope.templateHash)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `document client short-circuits offline and the hook sees the decorated request`() = runBlocking {
        val stub = StubInterceptor("<html>STUBBED</html>")
        RoverNetworkTestHook.interceptor = stub

        val document = documentClient().fetchDocument(url("/a/home"))

        assertEquals("<html>STUBBED</html>", document.html)
        assertEquals("\"stub-v1\"", document.etag)
        assertEquals(AppScreenDataScope.PERSONALIZED, document.dataScope)

        // Nothing left the device: the application interceptor ran before DNS, the connection, and
        // the disk cache.
        assertEquals(0, server.requestCount)

        val observed = stub.observed!!
        assertEquals("/a/home", observed.url.encodedPath)
        // Added last, so the client's own User-Agent decoration is already applied.
        assertNotNull(observed.header("User-Agent"))
    }

    @Test
    fun `document client short-circuit survives a repeat fetch and never consults the network`() =
        runBlocking {
            RoverNetworkTestHook.interceptor = StubInterceptor("<html>STUBBED</html>")

            val client = documentClient()
            client.fetchDocument(url("/a/home"))
            client.fetchDocument(
                url("/a/home"),
                AppScreensDocumentClient.DocumentCachePolicy.ForceRevalidate
            )

            assertEquals(0, server.requestCount)
        }

    @Test
    fun `data client short-circuits offline and the hook sees identifiers and authorization`() =
        runBlocking {
            val stub = StubInterceptor("""{"templateHash":"stub-h1"}""")
            RoverNetworkTestHook.interceptor = stub

            val envelope =
                dataClient().fetchScreenData(url("/a/home"), AppScreenDataScope.PERSONALIZED)

            assertEquals("stub-h1", envelope.templateHash)
            assertEquals(AppScreenDataScope.PERSONALIZED, envelope.responseScope)
            assertEquals(0, server.requestCount)

            val observed = stub.observed!!
            assertEquals("/a/home.json", observed.url.encodedPath)
            // The personalized discriminators the mock network keys provenance off.
            assertEquals("device-abc", observed.url.queryParameter("deviceIdentifier"))
            assertEquals("user-123", observed.url.queryParameter("userID"))
            assertEquals("Bearer FAKE_JWT", observed.header("Authorization"))
            assertNotNull(observed.header("User-Agent"))
        }

    @Test
    fun `data client public short-circuit carries no identifiers`() = runBlocking {
        val stub = StubInterceptor("""{"templateHash":"stub-h1"}""", scope = "public")
        RoverNetworkTestHook.interceptor = stub

        dataClient().fetchScreenData(url("/a/home"), AppScreenDataScope.PUBLIC)

        assertEquals(0, server.requestCount)

        // The anonymous-vs-personalized discriminator the mock network keys provenance off must
        // still be visible (by its absence) through the hook.
        val observed = stub.observed!!
        assertNull(observed.url.queryParameter("deviceIdentifier"))
        assertNull(observed.url.queryParameter("userID"))
        assertNull(observed.header("Authorization"))
    }

    // region fakes

    private class FakeAuthenticationContext(
        private val enabledDomains: Set<String>,
        private val token: String?
    ) : AuthenticationContextInterface {
        override val sdkToken: String? = "sdk-token"
        override val sdkAuthenticationEnabledDomains: Set<String> = enabledDomains
        override fun setSdkAuthenticationIdToken(token: String?) {}
        override fun clearSdkAuthenticationIdToken() {}
        override var sdkAuthenticationIdTokenRefreshCallback: () -> Unit = {}
        override suspend fun obtainSdkAuthenticationIdToken(checkValidity: Boolean): String? = token
        override fun enableSdkAuthIdTokenRefreshForDomain(pattern: String) {}
    }

    private class FakeUserInfo(private val userIdentifier: String?) : UserInfoInterface {
        override fun update(builder: (attributes: HashMap<String, Any>) -> Unit) {}
        override fun clear() {}
        override val currentUserInfo: Attributes
            get() = userIdentifier?.let { mapOf("userID" to it) } ?: emptyMap()
        override val reportedUserInfo: Attributes
            get() = currentUserInfo
    }

    private class FakeDeviceIdentification(
        override val installationIdentifier: String
    ) : DeviceIdentificationInterface {
        override var deviceName: String? = "Test Phone"
    }

    // endregion
}
