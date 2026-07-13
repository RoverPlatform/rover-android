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

package io.rover.sdk.core.data.http

import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OkHttpNetworkClientTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun onSubscribeCalledBeforeOnNext() {
        // Regression test: newCallPublisher previously never called subscriber.onSubscribe(),
        // causing 'onNext' was called before 'onSubscribe' crashes in spec-compliant subscribers
        // such as kotlinx-coroutines-reactive's awaitFirst().
        server.enqueue(MockResponse().setBody("hello"))

        val onSubscribeCalled = AtomicBoolean(false)
        val onNextBeforeOnSubscribe = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val request = Request.Builder().url(server.url("/")).build()
        client.newCallPublisher(request).subscribe(object : Subscriber<okhttp3.Response> {
            override fun onSubscribe(s: Subscription) {
                onSubscribeCalled.set(true)
                s.request(Long.MAX_VALUE)
            }
            override fun onNext(t: okhttp3.Response) {
                if (!onSubscribeCalled.get()) onNextBeforeOnSubscribe.set(true)
            }
            override fun onError(t: Throwable) { latch.countDown() }
            override fun onComplete() { latch.countDown() }
        })

        assertTrue("timed out waiting for response", latch.await(5, TimeUnit.SECONDS))
        assertFalse("onNext was called before onSubscribe", onNextBeforeOnSubscribe.get())
        assertTrue("onSubscribe should have been called", onSubscribeCalled.get())
    }

    @Test
    fun awaitFirstReceivesResponse() {
        // Verifies awaitFirst() (a spec-strict subscriber) works end-to-end with newCallPublisher.
        // Note: response.body is not asserted here — awaitFirst() cancels the subscription after
        // the first item, which cancels the OkHttp call and closes the body stream.
        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder().url(server.url("/")).build()
        val response = runBlocking {
            client.newCallPublisher(request).awaitFirst()
        }

        assertEquals(200, response.code)
    }
}
