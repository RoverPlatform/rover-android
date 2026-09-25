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

import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.events.contextproviders.UserInfoContextProvider
import io.rover.sdk.core.platform.InMemoryLocalStorage
import io.rover.sdk.core.privacy.PrivacyService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the three places user info leaves the SDK: the device context attached to every event,
 * the identifier resolved for Engage and App Screens requests, and the value handed to
 * Experiences for interpolation. In every case an anonymized tracking mode withholds the
 * ticketing identities without ever erasing them from storage.
 */
@RunWith(RobolectricTestRunner::class)
class UserInfoPrivacyEgressTest {

    private lateinit var localStorage: InMemoryLocalStorage
    private lateinit var privacyService: PrivacyService
    private lateinit var userInfo: UserInfo

    @Before
    fun setUp() {
        localStorage = InMemoryLocalStorage()
        privacyService = PrivacyService(localStorage)
        userInfo = UserInfo(localStorage, privacyService)
    }

    private fun signInEverywhere() {
        userInfo.update {
            it["ticketmaster"] = hashMapOf<String, Any>("ticketmasterID" to "tm-123")
            it["seatGeek"] = hashMapOf<String, Any>("seatGeekClientID" to "sg-client")
            it["axs"] = hashMapOf<String, Any>("userID" to "axs-1")
            it["flashMemberID"] = "flash-member"
            it["flashMobileID"] = "flash-mobile"
            it["ecid"] = "ecid-1"
            it["favouriteTeam"] = "Mudslingers"
        }
    }

    private fun capturedUserInfo(): Map<String, Any> =
        UserInfoContextProvider(userInfo).captureContext(DeviceContext.blank()).userInfo

    // region event device context

    @Test
    fun `events carry the identities in default mode`() {
        signInEverywhere()

        val captured = capturedUserInfo()

        assertEquals(mapOf("ticketmasterID" to "tm-123"), captured["ticketmaster"])
        assertEquals("ecid-1", captured["ecid"])
        assertEquals("Mudslingers", captured["favouriteTeam"])
    }

    @Test
    fun `events carry no sensitive keys while anonymized, and the stored value is untouched`() {
        signInEverywhere()

        privacyService.trackingMode = PrivacyService.TrackingMode.Anonymized

        val captured = capturedUserInfo()
        UserInfoKeys.privacySensitiveKeys.forEach { key ->
            assertFalse("expected $key to be withheld", captured.containsKey(key))
        }
        assertEquals("Mudslingers", captured["favouriteTeam"])

        // withheld, not erased.
        UserInfoKeys.privacySensitiveKeys.forEach { key ->
            assertTrue("expected $key to still be stored", userInfo.currentUserInfo.containsKey(key))
        }
    }

    @Test
    fun `switching back to default reports the identity again without a fresh sign in`() {
        signInEverywhere()
        privacyService.trackingMode = PrivacyService.TrackingMode.Anonymized
        privacyService.trackingMode = PrivacyService.TrackingMode.Default

        assertEquals(mapOf("ticketmasterID" to "tm-123"), capturedUserInfo()["ticketmaster"])
    }

    @Test
    fun `an identity written while anonymized is stored, withheld, and reported after switching back`() {
        privacyService.trackingMode = PrivacyService.TrackingMode.Anonymized

        userInfo.update {
            it["ticketmaster"] = hashMapOf<String, Any>("ticketmasterID" to "tm-late")
        }

        assertEquals(
            mapOf("ticketmasterID" to "tm-late"),
            userInfo.currentUserInfo["ticketmaster"]
        )
        assertFalse(capturedUserInfo().containsKey("ticketmaster"))

        privacyService.trackingMode = PrivacyService.TrackingMode.Default

        assertEquals(mapOf("ticketmasterID" to "tm-late"), capturedUserInfo()["ticketmaster"])
    }

    @Test
    fun `host set keys outside the blacklist are reported in either mode`() {
        userInfo.update {
            it["favouriteTeam"] = "Mudslingers"
            it["userID"] = "user-abc"
        }

        assertEquals("Mudslingers", capturedUserInfo()["favouriteTeam"])

        privacyService.trackingMode = PrivacyService.TrackingMode.Anonymized

        assertEquals("Mudslingers", capturedUserInfo()["favouriteTeam"])
        assertEquals("user-abc", capturedUserInfo()["userID"])
    }

    // endregion

    // region identifier resolution (Engage, App Screens)

    @Test
    fun `resolveUserIdentifier withholds the ticketing identity while anonymized`() {
        userInfo.update {
            it["ticketmaster"] = hashMapOf<String, Any>("ticketmasterID" to "tm-123")
        }

        assertEquals("tm-123", userInfo.resolveUserIdentifier())

        privacyService.trackingMode = PrivacyService.TrackingMode.Anonymized

        assertNull(userInfo.resolveUserIdentifier())

        privacyService.trackingMode = PrivacyService.TrackingMode.Default

        assertEquals("tm-123", userInfo.resolveUserIdentifier())
    }

    @Test
    fun `resolveUserIdentifier still resolves a host set userID while anonymized`() {
        userInfo.update {
            it["userID"] = "user-abc"
            it["seatGeek"] = hashMapOf<String, Any>("seatGeekClientID" to "sg-client")
        }

        privacyService.trackingMode = PrivacyService.TrackingMode.Anonymized

        assertEquals("user-abc", userInfo.resolveUserIdentifier())
    }

    // endregion

    // region experiences interpolation

    @Test
    fun `the value handed to experiences is the reported view`() {
        signInEverywhere()
        privacyService.trackingMode = PrivacyService.TrackingMode.Anonymized

        assertFalse(userInfo.reportedUserInfo.containsKey("seatGeek"))
        assertEquals("Mudslingers", userInfo.reportedUserInfo["favouriteTeam"])
    }

    // endregion

    @Test
    fun `a tracking mode change never mutates the stored user info`() {
        signInEverywhere()
        val before = HashMap(userInfo.currentUserInfo)

        userInfo.onTrackingModeChanged(PrivacyService.TrackingMode.Anonymized)
        userInfo.onTrackingModeChanged(PrivacyService.TrackingMode.Default)

        assertEquals(before, userInfo.currentUserInfo)
    }

    @Test
    fun `a relaunch in anonymized mode withholds without erasing`() {
        signInEverywhere()
        privacyService.trackingMode = PrivacyService.TrackingMode.Anonymized

        // simulate a process restart against the same persistent storage.
        val restartedPrivacyService = PrivacyService(localStorage)
        val restartedUserInfo = UserInfo(localStorage, restartedPrivacyService)

        assertEquals(
            PrivacyService.TrackingMode.Anonymized,
            restartedPrivacyService.trackingMode
        )
        assertEquals(
            mapOf("ticketmasterID" to "tm-123"),
            restartedUserInfo.currentUserInfo["ticketmaster"]
        )
        assertFalse(restartedUserInfo.reportedUserInfo.containsKey("ticketmaster"))

        restartedPrivacyService.trackingMode = PrivacyService.TrackingMode.Default

        assertEquals(
            mapOf("ticketmasterID" to "tm-123"),
            restartedUserInfo.reportedUserInfo["ticketmaster"]
        )
    }

    @Test
    fun `clearing user info empties both the stored and reported views`() {
        signInEverywhere()

        userInfo.clear()

        assertEquals(emptyMap<String, Any>(), userInfo.currentUserInfo)
        assertEquals(emptyMap<String, Any>(), userInfo.reportedUserInfo)
    }
}
