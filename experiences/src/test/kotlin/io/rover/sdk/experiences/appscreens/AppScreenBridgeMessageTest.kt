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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// org.json.JSONObject is an Android framework class stubbed to throw in plain unit tests, so these
// parse tests run under Robolectric to get the real JSON implementation. No WebView is touched.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppScreenBridgeMessageTest {

    private fun parse(json: String): BridgeMessage? = BridgeMessage.parse(JSONObject(json))

    @Test
    fun `loaded parses to Loaded`() {
        assertTrue(parse("""{"type":"loaded"}""") is BridgeMessage.Loaded)
    }

    @Test
    fun `navigate without optimisticData or transition`() {
        val message = parse("""{"type":"navigate","href":"/a/detail"}""")
        assertTrue(message is BridgeMessage.Navigate)
        message as BridgeMessage.Navigate
        assertEquals("/a/detail", message.href)
        assertNull(message.optimisticData)
        assertNull(message.transition)
    }

    @Test
    fun `navigate with optimisticData preserves raw json text`() {
        val message = parse(
            """{"type":"navigate","href":"/a/detail","optimisticData":{"id":7,"name":"x"},"transition":"push"}"""
        )
        assertTrue(message is BridgeMessage.Navigate)
        message as BridgeMessage.Navigate
        assertEquals("/a/detail", message.href)
        assertEquals("push", message.transition)
        // Optimistic data survives as parseable JSON carrying the same values.
        val optimisticData = JSONObject(message.optimisticData!!)
        assertEquals(7, optimisticData.getInt("id"))
        assertEquals("x", optimisticData.getString("name"))
    }

    @Test
    fun `navigate without href is null`() {
        assertNull(parse("""{"type":"navigate"}"""))
    }

    @Test
    fun `links parses href list`() {
        val message = parse("""{"type":"links","hrefs":["/a/one","/a/two"]}""")
        assertTrue(message is BridgeMessage.Links)
        message as BridgeMessage.Links
        assertEquals(listOf("/a/one", "/a/two"), message.hrefs)
    }

    @Test
    fun `links without array is null`() {
        assertNull(parse("""{"type":"links"}"""))
    }

    @Test
    fun `callResult ok carries raw result`() {
        val message = parse("""{"type":"callResult","id":3,"ok":true,"result":{"hydrateMs":42}}""")
        assertTrue(message is BridgeMessage.CallResult)
        message as BridgeMessage.CallResult
        assertEquals(3, message.id)
        assertTrue(message.ok)
        assertEquals(42, JSONObject(message.result!!).getInt("hydrateMs"))
        assertNull(message.error)
    }

    @Test
    fun `callResult error`() {
        val message = parse("""{"type":"callResult","id":5,"ok":false,"error":"boom"}""")
        assertTrue(message is BridgeMessage.CallResult)
        message as BridgeMessage.CallResult
        assertEquals(5, message.id)
        assertFalse(message.ok)
        // A JSON string value comes back from opt() as a plain String, so its raw form is unquoted.
        assertEquals("boom", message.error)
    }

    @Test
    fun `callResult without id is null`() {
        assertNull(parse("""{"type":"callResult","ok":true}"""))
    }

    @Test
    fun `openURL with dismiss true`() {
        val message = parse("""{"type":"openURL","href":"https://example.com","dismiss":true}""")
        assertTrue(message is BridgeMessage.OpenURL)
        message as BridgeMessage.OpenURL
        assertEquals("https://example.com", message.href)
        assertTrue(message.dismiss)
    }

    @Test
    fun `openURL with dismiss false`() {
        val message = parse("""{"type":"openURL","href":"https://example.com","dismiss":false}""")
        assertTrue(message is BridgeMessage.OpenURL)
        message as BridgeMessage.OpenURL
        assertFalse(message.dismiss)
    }

    @Test
    fun `openURL with dismiss absent defaults to false`() {
        val message = parse("""{"type":"openURL","href":"https://example.com"}""")
        assertTrue(message is BridgeMessage.OpenURL)
        message as BridgeMessage.OpenURL
        assertFalse(message.dismiss)
    }

    @Test
    fun `openURL with non-bool dismiss defaults to false`() {
        val message = parse("""{"type":"openURL","href":"https://example.com","dismiss":"maybe"}""")
        assertTrue(message is BridgeMessage.OpenURL)
        message as BridgeMessage.OpenURL
        assertFalse(message.dismiss)
    }

    @Test
    fun `openURL with blank href is null`() {
        assertNull(parse("""{"type":"openURL","href":"   "}"""))
    }

    @Test
    fun `openURL without href is null`() {
        assertNull(parse("""{"type":"openURL","dismiss":true}"""))
    }

    @Test
    fun `presentWebsite parses href`() {
        val message = parse("""{"type":"presentWebsite","href":"https://example.com/page"}""")
        assertTrue(message is BridgeMessage.PresentWebsite)
        message as BridgeMessage.PresentWebsite
        assertEquals("https://example.com/page", message.href)
    }

    @Test
    fun `presentWebsite with blank href is null`() {
        assertNull(parse("""{"type":"presentWebsite","href":""}"""))
    }

    @Test
    fun `presentWebsite without href is null`() {
        assertNull(parse("""{"type":"presentWebsite"}"""))
    }

    @Test
    fun `unknown type is null`() {
        assertNull(parse("""{"type":"somethingElse"}"""))
    }

    @Test
    fun `missing type is null`() {
        assertNull(parse("""{"href":"/a/x"}"""))
    }
}
