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
import io.rover.sdk.experiences.appscreens.AppScreenDataScope
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppScreensDataClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(path: String): Uri = Uri.parse(server.url(path).toString())

    private fun client(
        userIdentifier: String? = "user-123",
        token: String? = "FAKE_JWT"
    ): AppScreensDataClient {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return AppScreensDataClient(
            context = context,
            authenticationContext = FakeAuthenticationContext(
                enabledDomains = setOf(server.hostName),
                token = token
            ),
            userInfo = FakeUserInfo(userIdentifier),
            deviceIdentification = FakeDeviceIdentification("device-abc")
        )
    }

    @Test
    fun `public request carries no identifiers auth or account token`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("""{"templateHash":"h1"}""")
        )

        client().fetchScreenData(url("/a/standings"), AppScreenDataScope.PUBLIC)

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("x-rover-account-token"))
        assertNull(request.requestUrl!!.queryParameter("deviceIdentifier"))
        assertNull(request.requestUrl!!.queryParameter("userID"))
    }

    @Test
    fun `personalized request carries identifiers and authorization`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(AppScreenDataScope.HEADER_NAME, "personalized")
                .setBody("""{"templateHash":"h1"}""")
        )

        client().fetchScreenData(url("/a/home"), AppScreenDataScope.PERSONALIZED)

        val request = server.takeRequest()
        assertEquals("Bearer FAKE_JWT", request.getHeader("Authorization"))
        assertEquals("device-abc", request.requestUrl!!.queryParameter("deviceIdentifier"))
        assertEquals("user-123", request.requestUrl!!.queryParameter("userID"))
    }

    @Test
    fun `personalized without resolvable user omits userID but keeps deviceIdentifier`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(AppScreenDataScope.HEADER_NAME, "personalized")
                .setBody("""{"templateHash":"h1"}""")
        )

        client(userIdentifier = null).fetchScreenData(url("/a/home"), AppScreenDataScope.PERSONALIZED)

        val request = server.takeRequest()
        assertEquals("device-abc", request.requestUrl!!.queryParameter("deviceIdentifier"))
        assertNull(request.requestUrl!!.queryParameter("userID"))
    }

    @Test
    fun `templateHash is extracted and response scope captured`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("""{"data":{},"templateHash":"fx-standings-v2"}""")
        )

        val envelope = client().fetchScreenData(url("/a/standings"), AppScreenDataScope.PUBLIC)

        assertEquals("fx-standings-v2", envelope.templateHash)
        assertEquals(AppScreenDataScope.PUBLIC, envelope.responseScope)
        assertEquals("""{"data":{},"templateHash":"fx-standings-v2"}""", envelope.rawJson)
    }

    @Test
    fun `missing templateHash yields null`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("""{"data":{}}""")
        )

        val envelope = client().fetchScreenData(url("/a/standings"), AppScreenDataScope.PUBLIC)

        assertNull(envelope.templateHash)
    }

    @Test
    fun `public flipping to personalized retries once with identifiers`() = runBlocking {
        // First (public) request comes back declaring personalized scope → one retry.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(AppScreenDataScope.HEADER_NAME, "personalized")
                .setBody("""{"templateHash":"h1"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(AppScreenDataScope.HEADER_NAME, "personalized")
                .setBody("""{"templateHash":"h1"}""")
        )

        client().fetchScreenData(url("/a/home"), AppScreenDataScope.PUBLIC)

        assertEquals(2, server.requestCount)

        val bare = server.takeRequest()
        assertNull(bare.getHeader("Authorization"))
        assertNull(bare.requestUrl!!.queryParameter("deviceIdentifier"))

        val retried = server.takeRequest()
        assertEquals("Bearer FAKE_JWT", retried.getHeader("Authorization"))
        assertEquals("device-abc", retried.requestUrl!!.queryParameter("deviceIdentifier"))
    }

    @Test
    fun `personalized response does not retry`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(AppScreenDataScope.HEADER_NAME, "personalized")
                .setBody("""{"templateHash":"h1"}""")
        )

        client().fetchScreenData(url("/a/home"), AppScreenDataScope.PERSONALIZED)

        assertEquals(1, server.requestCount)
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
    }

    private class FakeDeviceIdentification(
        override val installationIdentifier: String
    ) : DeviceIdentificationInterface {
        override var deviceName: String? = "Test Phone"
    }

    // endregion
}
