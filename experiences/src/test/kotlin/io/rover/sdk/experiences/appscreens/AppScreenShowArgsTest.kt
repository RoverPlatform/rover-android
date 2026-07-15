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

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// org.json.JSONObject is an Android framework class stubbed to throw in plain unit tests, so these
// run under Robolectric to get the real implementation used by AppScreenShowArgs.build.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppScreenShowArgsTest {

    @Test
    fun `href only`() {
        val args = AppScreenShowArgs.build("https://x/a/home")
        val parsed = JSONObject(args)
        assertEquals("https://x/a/home", parsed.getString("href"))
        assertFalse(parsed.has("optimisticData"))
        assertFalse(parsed.has("response"))
    }

    @Test
    fun `optimisticData is spliced verbatim as a json value`() {
        val optimisticData = """{"id":7,"name":"Jaylen","nested":{"k":[1,2,3]}}"""
        val args = AppScreenShowArgs.build("https://x/a/player-detail?id=7", optimisticDataJson = optimisticData)

        // The exact optimisticData bytes appear inline (not re-quoted as a string).
        assertTrue(args.contains(""""optimisticData":$optimisticData"""))

        // And it round-trips as a structured JSON object carrying the same values.
        val parsed = JSONObject(args)
        val parsedOptimisticData = parsed.getJSONObject("optimisticData")
        assertEquals(7, parsedOptimisticData.getInt("id"))
        assertEquals("Jaylen", parsedOptimisticData.getString("name"))
        assertEquals(3, parsedOptimisticData.getJSONObject("nested").getJSONArray("k").length())
    }

    @Test
    fun `optimisticData and response both present morph args`() {
        val optimisticData = """{"id":7}"""
        val response = """{"data":{"x":1},"templateHash":"h1"}"""
        val args = AppScreenShowArgs.build("https://x/a/player-detail?id=7", optimisticDataJson = optimisticData, responseJson = response)

        assertTrue(args.contains(""""optimisticData":$optimisticData"""))
        assertTrue(args.contains(""""response":$response"""))
        val parsed = JSONObject(args)
        assertEquals("h1", parsed.getJSONObject("response").getString("templateHash"))
        assertEquals(7, parsed.getJSONObject("optimisticData").getInt("id"))
    }

    @Test
    fun `response only hydrate skips optimisticData key`() {
        val response = """{"data":{}}"""
        val args = AppScreenShowArgs.build("https://x/a/home", responseJson = response)
        val parsed = JSONObject(args)
        assertFalse(parsed.has("optimisticData"))
        assertTrue(parsed.has("response"))
    }

    @Test
    fun `href with quotes is escaped`() {
        val args = AppScreenShowArgs.build("""https://x/a/x?q="v"""")
        val parsed = JSONObject(args)
        assertEquals("""https://x/a/x?q="v"""", parsed.getString("href"))
    }

    // region ShowPayload replay fidelity (lastShowPayload)

    @Test
    fun `hydrate payload replays to href plus optimisticData with no response`() {
        // A hydrate records (href, optimisticData, null): recovery must replay those exact bytes.
        val optimisticData = """{"id":7}"""
        val payload = ShowPayload(
            href = "https://x/a/player-detail?id=7",
            optimisticDataJson = optimisticData,
            responseJson = null,
            templateHash = null
        )
        val expected = AppScreenShowArgs.build(payload.href, payload.optimisticDataJson, payload.responseJson)
        assertEquals(expected, payload.toArgs())

        val parsed = JSONObject(payload.toArgs())
        assertEquals("https://x/a/player-detail?id=7", parsed.getString("href"))
        assertEquals(7, parsed.getJSONObject("optimisticData").getInt("id"))
        assertFalse(parsed.has("response"))
    }

    @Test
    fun `morph payload supersedes hydrate and replays the full response verbatim`() {
        // A morph updates lastShowPayload to include the response raw JSON; replay repaints it all.
        val optimisticData = """{"id":7}"""
        val response = """{"data":{"pts":30},"templateHash":"fx-detail-v9"}"""
        val payload = ShowPayload(
            href = "https://x/a/player-detail?id=7",
            optimisticDataJson = optimisticData,
            responseJson = response,
            templateHash = "fx-detail-v9"
        )
        val args = payload.toArgs()
        // The full response crosses byte-perfect on replay (not re-serialized).
        assertTrue(args.contains(""""response":$response"""))
        assertTrue(args.contains(""""optimisticData":$optimisticData"""))

        val parsed = JSONObject(args)
        assertEquals("fx-detail-v9", parsed.getJSONObject("response").getString("templateHash"))
        assertEquals(30, parsed.getJSONObject("response").getJSONObject("data").getInt("pts"))
    }

    // endregion
}
