/*
 * Copyright (c) 2026, Rover Labs, Inc. All rights reserved.
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

package io.rover.sdk.experiences.rich.compose.ui.fonts

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import io.rover.sdk.experiences.rich.compose.data.ExperiencesHttpClient
import io.rover.sdk.experiences.rich.compose.model.values.DocumentFont
import io.rover.sdk.experiences.rich.compose.ui.RemoteAssetContext
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Covers the network behaviour of [FontLoader] for remotely hosted experiences:
 * duplicate font sources must not cause duplicate downloads, and a font downloaded once
 * must be served from disk on subsequent loads (SDK-406).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FontLoaderTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var loader: FontLoader
    private lateinit var assetContext: RemoteAssetContext

    private val fontBytes: ByteArray = "not really a ttf, but enough for the shadow Typeface".toByteArray()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, "RoverExperiences").deleteRecursively()

        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path?.startsWith("/fonts/") == true) {
                    MockResponse().setResponseCode(200).setBody(Buffer().write(fontBytes))
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        // No CDN configuration, so fonts resolve to <base>/fonts/<assetName>.
        assetContext = RemoteAssetContext(Uri.parse(server.url("/").toString()))
        loader = FontLoader(context, ExperiencesHttpClient(context))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun source(assetName: String, fontName: String) =
        DocumentFont.Source(assetName = assetName, fontNames = listOf(fontName))

    private fun fontRequestPaths(): List<String> {
        val paths = mutableListOf<String>()
        while (true) {
            val request = server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            request.path?.let { paths.add(it) }
        }
        return paths
    }

    @Test
    fun `duplicate sources for the same asset are downloaded once`() = runBlocking {
        // Mirrors a real customer document: every font entry repeats the whole family,
        // so the same asset appears many times in the flattened source list.
        val sources = List(4) { source("aaaa.ttf", "Roboto-Regular") } +
            List(4) { source("bbbb.ttf", "Roboto-Bold") }

        val mapping = loader.getTypefaceMappings(context, assetContext, sources)

        assertThat(mapping.mapping.keys, equalTo(setOf("Roboto-Regular", "Roboto-Bold")))
        // each unique asset should be fetched exactly once
        assertThat(fontRequestPaths().sorted(), equalTo(listOf("/fonts/aaaa.ttf", "/fonts/bbbb.ttf")))
    }

    @Test
    fun `a previously downloaded font is served from disk without a network request`() = runBlocking {
        val sources = listOf(source("aaaa.ttf", "Roboto-Regular"))

        loader.getTypefaceMappings(context, assetContext, sources)
        assertThat(fontRequestPaths().size, equalTo(1))

        val second = loader.getTypefaceMappings(context, assetContext, sources)

        assertThat(second.mapping.keys, equalTo(setOf("Roboto-Regular")))
        // second load should not touch the network
        assertThat(fontRequestPaths(), isEmpty)
    }

    @Test
    fun `an empty cached file is treated as missing and re-downloaded`() = runBlocking {
        // A crash between creating and filling the cache entry can leave a zero-length file.
        val fontsFolder = File(context.cacheDir, "RoverExperiences/fonts").apply { mkdirs() }
        File(fontsFolder, "aaaa.ttf").writeBytes(ByteArray(0))

        val mapping = loader.getTypefaceMappings(context, assetContext, listOf(source("aaaa.ttf", "Roboto-Regular")))

        assertThat(mapping.mapping.keys, equalTo(setOf("Roboto-Regular")))
        assertThat(fontRequestPaths(), equalTo(listOf("/fonts/aaaa.ttf")))
        assertThat(File(fontsFolder, "aaaa.ttf").length(), equalTo(fontBytes.size.toLong()))
    }

    @Test
    fun `an asset name that escapes the fonts cache folder is refused`() = runBlocking {
        val mapping = loader.getTypefaceMappings(
            context,
            assetContext,
            listOf(source("../escaped.ttf", "Evil-Regular"))
        )

        assertThat(mapping.mapping.keys, isEmpty)
        // nothing may be written outside the fonts folder
        assertThat(File(context.cacheDir, "RoverExperiences/escaped.ttf").exists(), equalTo(false))
    }

    @Test
    fun `every font name across duplicate sources is mapped`() = runBlocking {
        // Two sources share an asset but declare different postscript names. Deduplicating
        // the download must not drop either mapping.
        val sources = listOf(
            source("aaaa.ttf", "Family-Regular"),
            source("aaaa.ttf", "Family-Book")
        )

        val mapping = loader.getTypefaceMappings(context, assetContext, sources)

        assertThat(mapping.mapping.keys, equalTo(setOf("Family-Regular", "Family-Book")))
        assertThat(fontRequestPaths().size, equalTo(1))
    }
}
