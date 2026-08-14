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

package io.rover.sdk.notifications.communicationhub.ui

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test

class HomeRouteContentTest {
    @Test
    fun inFlightHomeURLHoldsInsteadOfFallingBackToInbox() {
        // The regression: while the home URL fetch is in flight the URL is null, and treating that
        // as "no home" composes the inbox fallback, whose reveal advances the seen watermark past
        // every stored post and zeroes the badge without the user seeing the inbox.
        assertThat(
            resolveHomeRouteContent(
                isHomeViewEnabled = true,
                isInboxEnabled = true,
                experienceURL = null,
                homeURLResolved = false,
            ),
            equalTo(HomeRouteContent.Pending),
        )
    }

    @Test
    fun resolvedWithNoHomeURLFallsBackToInbox() {
        assertThat(
            resolveHomeRouteContent(
                isHomeViewEnabled = true,
                isInboxEnabled = true,
                experienceURL = null,
                homeURLResolved = true,
            ),
            equalTo(HomeRouteContent.Inbox),
        )
    }

    @Test
    fun homeURLShowsHome() {
        assertThat(
            resolveHomeRouteContent(
                isHomeViewEnabled = true,
                isInboxEnabled = true,
                experienceURL = "https://example.com/home",
                homeURLResolved = true,
            ),
            equalTo(HomeRouteContent.Home),
        )
    }

    @Test
    fun cachedHomeURLShowsHomeEvenBeforeTheFetchResolves() {
        assertThat(
            resolveHomeRouteContent(
                isHomeViewEnabled = true,
                isInboxEnabled = true,
                experienceURL = "https://example.com/home",
                homeURLResolved = false,
            ),
            equalTo(HomeRouteContent.Home),
        )
    }

    @Test
    fun homeDisabledShowsInboxImmediately() {
        // With home disabled there is no fetch to wait on, so the Hub composable initialises the
        // resolved flag to true and the inbox is the route's real destination from the first frame.
        assertThat(
            resolveHomeRouteContent(
                isHomeViewEnabled = false,
                isInboxEnabled = true,
                experienceURL = null,
                homeURLResolved = true,
            ),
            equalTo(HomeRouteContent.Inbox),
        )
    }

    @Test
    fun bothDisabledShowsEmptySurface() {
        assertThat(
            resolveHomeRouteContent(
                isHomeViewEnabled = false,
                isInboxEnabled = false,
                experienceURL = null,
                homeURLResolved = true,
            ),
            equalTo(HomeRouteContent.Empty),
        )
    }

    @Test
    fun inboxDisabledWithResolvedNullHomeURLShowsEmptySurface() {
        // Pre-existing edge behaviour: home enabled but with no home experience, and no inbox to
        // fall back to, still lands on the empty themed surface.
        assertThat(
            resolveHomeRouteContent(
                isHomeViewEnabled = true,
                isInboxEnabled = false,
                experienceURL = null,
                homeURLResolved = true,
            ),
            equalTo(HomeRouteContent.Empty),
        )
    }

    @Test
    fun inboxDisabledStillHoldsWhileTheHomeURLFetchIsInFlight() {
        assertThat(
            resolveHomeRouteContent(
                isHomeViewEnabled = true,
                isInboxEnabled = false,
                experienceURL = null,
                homeURLResolved = false,
            ),
            equalTo(HomeRouteContent.Pending),
        )
    }
}
