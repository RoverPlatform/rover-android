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

package io.rover.sdk.core.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserInfoResolveIdentifierTest {

    private fun userInfo(map: Map<String, Any> = emptyMap()): UserInfoInterface {
        val attrs = HashMap<String, Any>(map)
        return object : UserInfoInterface {
            override fun update(builder: (attributes: HashMap<String, Any>) -> Unit) = Unit
            override fun clear() = Unit
            override val currentUserInfo get() = attrs
        }
    }

    @Test
    fun `returns userID when present`() {
        assertEquals("user-abc", userInfo(mapOf("userID" to "user-abc")).resolveUserIdentifier())
    }

    @Test
    fun `returns ticketmasterID when no userID`() {
        assertEquals(
            "tm-123",
            userInfo(mapOf("ticketmaster" to mapOf("ticketmasterID" to "tm-123"))).resolveUserIdentifier()
        )
    }

    @Test
    fun `returns seatGeekClientID when no userID or ticketmaster`() {
        assertEquals(
            "sg-client-123",
            userInfo(mapOf("seatGeek" to mapOf("seatGeekClientID" to "sg-client-123"))).resolveUserIdentifier()
        )
    }

    @Test
    fun `returns seatGeekID when no userID ticketmaster or clientID`() {
        assertEquals(
            "sg-456",
            userInfo(mapOf("seatGeek" to mapOf("seatGeekID" to "sg-456"))).resolveUserIdentifier()
        )
    }

    @Test
    fun `prefers userID over ticketmaster`() {
        assertEquals(
            "direct-user",
            userInfo(mapOf(
                "userID" to "direct-user",
                "ticketmaster" to mapOf("ticketmasterID" to "tm-123"),
            )).resolveUserIdentifier()
        )
    }

    @Test
    fun `prefers ticketmaster over seatGeekClientID`() {
        assertEquals(
            "tm-123",
            userInfo(mapOf(
                "ticketmaster" to mapOf("ticketmasterID" to "tm-123"),
                "seatGeek" to mapOf("seatGeekClientID" to "sg-client-abc"),
            )).resolveUserIdentifier()
        )
    }

    @Test
    fun `prefers seatGeekClientID over seatGeekID`() {
        assertEquals(
            "sg-client-abc",
            userInfo(mapOf(
                "seatGeek" to mapOf(
                    "seatGeekClientID" to "sg-client-abc",
                    "seatGeekID" to "sg-crm-xyz",
                ),
            )).resolveUserIdentifier()
        )
    }

    @Test
    fun `returns null when no identifiers present`() {
        assertNull(userInfo().resolveUserIdentifier())
    }

    @Test
    fun `returns null when all identifiers are blank`() {
        assertNull(
            userInfo(mapOf(
                "userID" to "   ",
                "ticketmaster" to mapOf("ticketmasterID" to ""),
                "seatGeek" to mapOf("seatGeekClientID" to "  ", "seatGeekID" to ""),
            )).resolveUserIdentifier()
        )
    }

    @Test
    fun `skips blank userID and falls through to ticketmaster`() {
        assertEquals(
            "tm-123",
            userInfo(mapOf(
                "userID" to "  ",
                "ticketmaster" to mapOf("ticketmasterID" to "tm-123"),
            )).resolveUserIdentifier()
        )
    }

    @Test
    fun `returns null when only unrelated keys present`() {
        assertNull(userInfo(mapOf("email" to "person@example.com")).resolveUserIdentifier())
    }
}
