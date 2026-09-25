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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two App Screens events' names and their EXACT attribute maps (asserted whole, so an extra key
 * fails). The builders are pure and carry their URLs verbatim — nothing is parsed — so unlike
 * AppScreensDecisionsTest these need no Robolectric and run on the plain JVM.
 */
class AppScreenEventsTest {

    // region App Screen Viewed

    @Test
    fun `viewed event name`() {
        assertEquals("App Screen Viewed", appScreenViewedEvent("https://app.example.com/a/home").name)
    }

    @Test
    fun `viewed event attributes are the screen url and nothing else`() {
        val event = appScreenViewedEvent("https://app.example.com/a/player-detail?id=dez-carter")
        assertEquals(
            mapOf("screenURL" to "https://app.example.com/a/player-detail?id=dez-carter"),
            event.attributes
        )
    }

    @Test
    fun `viewed event carries the url verbatim, query and all`() {
        val url = "https://app.example.com:8443/a/standings/?season=2026#table"
        assertEquals(url, appScreenViewedEvent(url).attributes["screenURL"])
    }

    // endregion

    // region App Screen Link Clicked

    @Test
    fun `link clicked event name`() {
        val event = appScreenLinkClickedEvent(
            screenURL = "https://app.example.com/a/home",
            linkURL = "https://app.example.com/a/standings"
        )
        assertEquals("App Screen Link Clicked", event.name)
    }

    @Test
    fun `link clicked event attributes are the two urls and nothing else`() {
        val event = appScreenLinkClickedEvent(
            screenURL = "https://app.example.com/a/home?tab=news",
            linkURL = "https://app.example.com/a/player-detail?id=dez-carter"
        )
        assertEquals(
            mapOf(
                "screenURL" to "https://app.example.com/a/home?tab=news",
                "linkURL" to "https://app.example.com/a/player-detail?id=dez-carter"
            ),
            event.attributes
        )
    }

    @Test
    fun `link clicked carries an external link verbatim`() {
        val event = appScreenLinkClickedEvent(
            screenURL = "https://app.example.com/a/home",
            linkURL = "mailto:support@example.com"
        )
        assertEquals("mailto:support@example.com", event.attributes["linkURL"])
    }

    // endregion
}
