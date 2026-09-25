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

package io.rover.sdk.core.routing

import android.content.Intent
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.sameInstance
import io.rover.sdk.core.logging.GlobalStaticLogHolder
import io.rover.sdk.core.logging.LogEmitter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URI

/**
 * The router used to run every registered route for every URI (side effects included) and had
 * no exception isolation, so one throwing route made the whole `route()` call throw: a dead
 * link from a push tap, and a crash through `Rover.intentForLink` and Experience link taps.
 * These pin the replacement: first match wins in registration order, later routes are not
 * consulted, and a throwing route is logged and skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RouterServiceTest {
    private class RecordingRoute(
        private val intent: Intent? = null,
        private val error: Exception? = null,
    ) : Route {
        val resolvedUris = mutableListOf<URI?>()

        override fun resolveUri(uri: URI?): Intent? {
            resolvedUris += uri
            error?.let { throw it }
            return intent
        }
    }

    private class RecordingLogEmitter : LogEmitter {
        val errors = mutableListOf<Pair<String, String>>()

        override fun e(logTag: String, message: String) {
            errors += logTag to message
        }

        override fun w(logTag: String, message: String) = Unit
        override fun v(logTag: String, message: String) = Unit
        override fun i(logTag: String, message: String) = Unit
        override fun d(logTag: String, message: String) = Unit
    }

    private val uri = URI("rv-app://posts/abc123")
    private val logEmitter = RecordingLogEmitter()
    private var previousLogEmitter: LogEmitter? = null

    @Before
    fun installLogEmitter() {
        previousLogEmitter = GlobalStaticLogHolder.globalLogEmitter
        GlobalStaticLogHolder.globalLogEmitter = logEmitter
    }

    @After
    fun restoreLogEmitter() {
        GlobalStaticLogHolder.globalLogEmitter = previousLogEmitter
    }

    @Test
    fun `a throwing route is skipped and a later route still handles the uri`() {
        val router = RouterService()
        val expected = Intent()
        val throwing = RecordingRoute(error = IllegalStateException("bad query"))
        val matching = RecordingRoute(intent = expected)
        router.registerRoute(throwing)
        router.registerRoute(matching)

        assertThat(router.route(uri, false), sameInstance(expected))
        assertThat(throwing.resolvedUris, equalTo(listOf<URI?>(uri)))
        assertThat(matching.resolvedUris, equalTo(listOf<URI?>(uri)))
    }

    @Test
    fun `a route that would throw after the winner is never consulted`() {
        val router = RouterService()
        val expected = Intent()
        val matching = RecordingRoute(intent = expected)
        val throwing = RecordingRoute(error = IllegalStateException("bad query"))
        router.registerRoute(matching)
        router.registerRoute(throwing)

        assertThat(router.route(uri, false), sameInstance(expected))
        assertThat(throwing.resolvedUris, equalTo(emptyList<URI?>()))
        assertThat(logEmitter.errors, equalTo(emptyList<Pair<String, String>>()))
    }

    @Test
    fun `the first matching route wins and later routes are not consulted`() {
        val router = RouterService()
        val firstIntent = Intent()
        val first = RecordingRoute(intent = firstIntent)
        val second = RecordingRoute(intent = Intent())
        router.registerRoute(first)
        router.registerRoute(second)

        val resolved = router.route(uri, false)

        assertThat(resolved, sameInstance(firstIntent))
        assertThat(first.resolvedUris.size, equalTo(1))
        assertThat(second.resolvedUris, equalTo(emptyList<URI?>()))
    }

    @Test
    fun `a non-matching route is passed over in registration order`() {
        val router = RouterService()
        val expected = Intent()
        val nonMatching = RecordingRoute(intent = null)
        val matching = RecordingRoute(intent = expected)
        router.registerRoute(nonMatching)
        router.registerRoute(matching)

        assertThat(router.route(uri, false), sameInstance(expected))
        assertThat(nonMatching.resolvedUris, equalTo(listOf<URI?>(uri)))
    }

    @Test
    fun `no matching route yields null`() {
        val router = RouterService()
        router.registerRoute(RecordingRoute(intent = null))

        assertThat(router.route(uri, false), absent())
    }

    @Test
    fun `a throwing route with no other match yields null instead of throwing`() {
        val router = RouterService()
        val throwing = RecordingRoute(error = IllegalStateException("bad query"))
        router.registerRoute(throwing)

        assertThat(router.route(uri, false), absent())
        assertThat(throwing.resolvedUris, equalTo(listOf<URI?>(uri)))
    }

    @Test
    fun `a throwing route is logged as an error naming the route and the exception type`() {
        val router = RouterService()
        router.registerRoute(RecordingRoute(error = IllegalStateException("bad query")))

        router.route(uri, false)

        val (tag, message) = logEmitter.errors.single()
        assertThat(tag, equalTo("Rover::RouterService"))
        assertThat(message, containsSubstring("RecordingRoute"))
        assertThat(message, containsSubstring(IllegalStateException::class.java.name))
        assertThat(message, containsSubstring("`rv-app://posts/abc123`"))
    }

    @Test
    fun `the log drops user info, query, fragment and the exception message`() {
        val router = RouterService()
        val secretUri = URI("rv-app://alice:hunter2@presentExperience/path?token=abc123#frag")
        router.registerRoute(RecordingRoute(error = IllegalArgumentException("could not parse token=abc123")))

        router.route(secretUri, false)

        val message = logEmitter.errors.single().second
        assertThat(message, containsSubstring("`rv-app://presentExperience/path`"))
        assertThat(message, !containsSubstring("hunter2"))
        assertThat(message, !containsSubstring("alice"))
        assertThat(message, !containsSubstring("abc123"))
        assertThat(message, !containsSubstring("frag"))
    }

    @Test
    fun `the log redacts an authority that did not parse into user info, host and port`() {
        val router = RouterService()
        router.registerRoute(RecordingRoute(error = IllegalStateException("bad query")))

        router.route(URI("rv-app://first@token=abc123/path"), false)
        router.route(URI("rv-app://[::1]:8080/path"), false)

        val messages = logEmitter.errors.map { it.second }
        assertThat(messages[0], containsSubstring("`rv-app://<authority>/path`"))
        assertThat(messages[0], !containsSubstring("first"))
        assertThat(messages[0], !containsSubstring("abc123"))
        assertThat(messages[1], containsSubstring("`rv-app://[::1]:8080/path`"))
    }

    @Test
    fun `the log renders relative, opaque and null uris by their shape`() {
        val throwing = RecordingRoute(error = IllegalStateException("bad query"))
        val router = RouterService()
        router.registerRoute(throwing)

        router.route(URI("/inbox/conversations?token=abc123"), false)
        router.route(URI("mailto:someone@example.com"), false)
        router.route(null, false)

        val messages = logEmitter.errors.map { it.second }
        assertThat(messages[0], containsSubstring("`/inbox/conversations`"))
        assertThat(messages[0], !containsSubstring("abc123"))
        assertThat(messages[1], containsSubstring("`mailto:`"))
        assertThat(messages[1], !containsSubstring("someone@example.com"))
        assertThat(messages[2], containsSubstring("`null`"))
    }
}
