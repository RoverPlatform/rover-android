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

import io.rover.sdk.core.data.domain.Attributes
import io.rover.sdk.core.privacy.PrivacyService.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the pure privacy filter that produces the reported view of user info.
 */
class UserInfoPrivacyFilterTest {

    private val fullyPopulated: Attributes = mapOf(
        "userID" to "user-abc",
        "ticketmaster" to mapOf("ticketmasterID" to "tm-123"),
        "seatGeek" to mapOf("seatGeekClientID" to "sg-client", "seatGeekID" to "sg-crm"),
        "axs" to mapOf("userID" to "axs-1"),
        "flashMemberID" to "flash-member",
        "flashMobileID" to "flash-mobile",
        "ecid" to "ecid-1",
        "favouriteTeam" to "Mudslingers",
        "tier" to 3
    )

    @Test
    fun `default mode reports the stored user info unchanged`() {
        assertEquals(fullyPopulated, filterUserInfoForPrivacy(fullyPopulated, TrackingMode.Default))
    }

    @Test
    fun `anonymized mode withholds every sensitive key`() {
        val reported = filterUserInfoForPrivacy(fullyPopulated, TrackingMode.Anonymized)

        assertEquals(
            mapOf(
                "userID" to "user-abc",
                "favouriteTeam" to "Mudslingers",
                "tier" to 3
            ),
            reported
        )
    }

    @Test
    fun `anonymized mode leaves host set keys alone`() {
        val stored: Attributes = mapOf(
            "favouriteTeam" to "Mudslingers",
            "seatPreferences" to mapOf("aisle" to true)
        )

        assertEquals(stored, filterUserInfoForPrivacy(stored, TrackingMode.Anonymized))
    }

    @Test
    fun `the sensitive key list is exactly the documented one`() {
        assertEquals(
            setOf("ticketmaster", "seatGeek", "axs", "flashMemberID", "flashMobileID", "ecid"),
            UserInfoKeys.privacySensitiveKeys
        )
    }

    @Test
    fun `filtering is idempotent`() {
        val once = filterUserInfoForPrivacy(fullyPopulated, TrackingMode.Anonymized)
        val twice = filterUserInfoForPrivacy(once, TrackingMode.Anonymized)

        assertEquals(once, twice)
    }

    @Test
    fun `filtering an empty user info yields an empty user info in either mode`() {
        assertEquals(emptyMap<String, Any>(), filterUserInfoForPrivacy(emptyMap(), TrackingMode.Default))
        assertEquals(emptyMap<String, Any>(), filterUserInfoForPrivacy(emptyMap(), TrackingMode.Anonymized))
    }
}
