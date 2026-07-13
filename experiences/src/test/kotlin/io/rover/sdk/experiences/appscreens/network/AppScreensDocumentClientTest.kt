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
import io.rover.sdk.experiences.appscreens.AppScreenDataScope
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppScreensDocumentClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AppScreensDocumentClient

    @Before
    fun setUp() {
        // Clear the shared Robolectric cache dir so cache state never leaks between tests.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.cacheDir, "rover-appscreens-http").deleteRecursively()

        server = MockWebServer()
        server.start()
        client = AppScreensDocumentClient(context)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(path: String): Uri = Uri.parse(server.url(path).toString())

    @Test
    fun `request carries no credential headers`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"fx-home-v4\"")
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("<html></html>")
        )

        val document = client.fetchDocument(url("/a/home"))

        assertEquals("\"fx-home-v4\"", document.etag)
        assertEquals(AppScreenDataScope.PUBLIC, document.dataScope)

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("x-rover-account-token"))
        assertTrue(request.path!!.startsWith("/a/home"))
        assertTrue(request.requestUrl!!.queryParameter("deviceIdentifier") == null)
        assertTrue(request.requestUrl!!.queryParameter("userID") == null)
    }

    @Test
    fun `fresh cache is served without a second network hit`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Cache-Control", "public, max-age=60")
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("HELLO")
        )

        val first = client.fetchDocument(url("/a/home"))
        val second = client.fetchDocument(url("/a/home"))

        assertEquals("HELLO", first.html)
        assertEquals("HELLO", second.html)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `stale entry revalidates with If-None-Match and 304 serves cached body`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Cache-Control", "max-age=0")
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("CACHED-BODY")
        )
        server.enqueue(MockResponse().setResponseCode(304))

        val first = client.fetchDocument(url("/a/home"))
        val second = client.fetchDocument(url("/a/home"))

        assertEquals("CACHED-BODY", first.html)
        // The 304 served the stored body.
        assertEquals("CACHED-BODY", second.html)
        assertEquals(2, server.requestCount)

        server.takeRequest() // first
        val revalidation = server.takeRequest()
        assertEquals("\"v1\"", revalidation.getHeader("If-None-Match"))
    }

    @Test
    fun `304 with changed scope header freshens stored scope`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Cache-Control", "max-age=0")
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("BODY")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(304)
                .setHeader(AppScreenDataScope.HEADER_NAME, "personalized")
        )

        val first = client.fetchDocument(url("/a/home"))
        val second = client.fetchDocument(url("/a/home"))

        assertEquals(AppScreenDataScope.PUBLIC, first.dataScope)
        // The scope header on the 304 must win — this is the OkHttp/URLCache parity the scope
        // design depends on.
        assertEquals(AppScreenDataScope.PERSONALIZED, second.dataScope)
    }

    @Test
    fun `ForceRevalidate sends If-None-Match even while fresh`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Cache-Control", "public, max-age=60")
                .setHeader(AppScreenDataScope.HEADER_NAME, "public")
                .setBody("BODY")
        )
        server.enqueue(MockResponse().setResponseCode(304))

        client.fetchDocument(url("/a/home"))
        client.fetchDocument(
            url("/a/home"),
            AppScreensDocumentClient.DocumentCachePolicy.ForceRevalidate
        )

        assertEquals(2, server.requestCount)
        server.takeRequest() // first
        val forced = server.takeRequest()
        assertEquals("\"v1\"", forced.getHeader("If-None-Match"))
    }
}
