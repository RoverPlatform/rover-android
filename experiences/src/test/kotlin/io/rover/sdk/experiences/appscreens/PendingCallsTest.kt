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

package io.rover.sdk.experiences.appscreens

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the fail-fast bookkeeping [AppScreenBridge.fail] delegates to on a renderer death:
 * every pending down-call must complete exceptionally so no caller waits out its timeout. Framework
 * free (no WebView / reply proxy), so it runs as a plain JVM test.
 */
class PendingCallsTest {

    @Test
    fun `failAll completes an outstanding call exceptionally`() = runBlocking {
        val pending = PendingCalls()
        val deferred = pending.register(1)

        // A caller is suspended awaiting the reply.
        val awaiter = async { runCatching { deferred.await() } }

        val cause = RenderProcessGoneException(didCrash = true)
        pending.failAll(cause)

        val result = awaiter.await()
        assertTrue(result.isFailure)
        assertEquals(cause, result.exceptionOrNull())
    }

    @Test
    fun `failAll fails every outstanding call and empties the bag`() = runBlocking {
        val pending = PendingCalls()
        val deferreds = listOf(pending.register(1), pending.register(2), pending.register(3))
        assertEquals(3, pending.size)

        pending.failAll(RenderProcessGoneException(didCrash = false))

        deferreds.forEach { deferred ->
            val outcome = runCatching { deferred.await() }
            assertTrue(outcome.isFailure)
            assertTrue(outcome.exceptionOrNull() is RenderProcessGoneException)
        }
        assertEquals(0, pending.size)
    }

    @Test
    fun `removed call is not affected by a later failAll`() {
        val pending = PendingCalls()
        val deferred = pending.register(7)
        pending.remove(7)
        assertEquals(0, pending.size)

        pending.failAll(RenderProcessGoneException())
        // The removed deferred was never completed by failAll.
        assertFalse(deferred.isCompleted)
    }
}
