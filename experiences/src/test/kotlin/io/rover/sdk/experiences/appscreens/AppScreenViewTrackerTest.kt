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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `App Screen Viewed` DECISIONS, which used to live inline in [AppScreenNavigator] and needed a
 * Context and bridged WebViews to reach: whether a push counts as an appearance, what a pop/dismiss
 * reveals, and when the reveal path must stay quiet. The tracker takes an entry id and a URL and
 * nothing else, so these run on the plain JVM with no Robolectric.
 *
 * Each test drives the same sequence the navigator would drive and asserts the tracker's answers
 * whole (the returned [AppScreenView], or — where the answer becomes an event — the event's entire
 * attribute map, so an extra key fails).
 *
 * The scenarios are named for the navigator call sites that produce them:
 *
 * | Navigator | Tracker |
 * |---|---|
 * | `establish` → `trackAppScreenViewedIfExposed(root)` | [AppScreenViewTracker.viewedIfExposed] with the root as the exposed top |
 * | `onBridgeNavigate` → `trackAppScreenViewedIfExposed(pushed)` | [AppScreenViewTracker.viewedIfExposed], exposed or not |
 * | `onPopped`/`onSheetPopped`/`onSheetDismissed` → `trackExposedTopViewed()` | [AppScreenViewTracker.revealed] |
 * | `teardown` | [AppScreenViewTracker.reset] |
 */
class AppScreenViewTrackerTest {

    private val home = AppScreenView("entry-1", "https://app.example.com/a/home")
    private val detail = AppScreenView("entry-2", "https://app.example.com/a/player-detail?id=dez-carter")
    private val sheet = AppScreenView("entry-3", "https://app.example.com/a/standings")

    // region establish: the root is exposed by construction

    @Test
    fun `establishing the root reports it`() {
        val tracker = AppScreenViewTracker()

        // establish() pushes onto freshly-cleared stacks, so the entry it pushed IS the exposed top.
        assertEquals(home, tracker.viewedIfExposed(home, exposedTop = home))
        assertEquals("entry-1", tracker.lastViewedEntryId)
    }

    @Test
    fun `the reported root becomes an event carrying its url and nothing else`() {
        val tracker = AppScreenViewTracker()

        val view = tracker.viewedIfExposed(home, exposedTop = home)!!

        assertEquals(
            mapOf("screenURL" to "https://app.example.com/a/home"),
            appScreenViewedEvent(view.screenURL).attributes
        )
    }

    // endregion

    // region pushes

    @Test
    fun `a push that is the exposed top reports it`() {
        val tracker = AppScreenViewTracker()
        tracker.viewedIfExposed(home, exposedTop = home)

        // A plain root-stack navigation with no sheet up: the pushed entry is on top.
        assertEquals(detail, tracker.viewedIfExposed(detail, exposedTop = detail))
        assertEquals("entry-2", tracker.lastViewedEntryId)
    }

    @Test
    fun `a push that landed behind a presented sheet reports nothing and claims nothing`() {
        val tracker = AppScreenViewTracker()
        tracker.viewedIfExposed(home, exposedTop = home)
        tracker.viewedIfExposed(sheet, exposedTop = sheet)

        // A `navigate` from the COVERED root lands on the root stack, under the sheet.
        assertNull(tracker.viewedIfExposed(detail, exposedTop = sheet))
        assertEquals(
            "A hidden push must not claim the last-reported entry, or its reveal would be suppressed",
            "entry-3",
            tracker.lastViewedEntryId
        )
    }

    @Test
    fun `a push hidden behind a sheet is reported when the sheet is dismissed`() {
        val tracker = AppScreenViewTracker()
        tracker.viewedIfExposed(home, exposedTop = home)
        tracker.viewedIfExposed(sheet, exposedTop = sheet)
        tracker.viewedIfExposed(detail, exposedTop = sheet)

        // onSheetDismissed(): the sheet stack is gone and the hidden push is now the exposed top.
        assertEquals(detail, tracker.revealed(exposedTop = detail))
        assertEquals("entry-2", tracker.lastViewedEntryId)
    }

    // endregion

    // region reveals

    @Test
    fun `popping back onto a screen reports it again`() {
        val tracker = AppScreenViewTracker()
        tracker.viewedIfExposed(home, exposedTop = home)
        tracker.viewedIfExposed(detail, exposedTop = detail)

        val revealed = tracker.revealed(exposedTop = home)

        assertEquals(home, revealed)
        assertEquals("entry-1", tracker.lastViewedEntryId)
        assertEquals(
            mapOf("screenURL" to "https://app.example.com/a/home"),
            appScreenViewedEvent(revealed!!.screenURL).attributes
        )
    }

    @Test
    fun `a reveal of the entry already reported is silent, however many times it arrives`() {
        val tracker = AppScreenViewTracker()
        tracker.viewedIfExposed(home, exposedTop = home)
        tracker.viewedIfExposed(detail, exposedTop = detail)
        assertEquals(home, tracker.revealed(exposedTop = home))

        // A reconciliation that removed nothing visible, and the same signal delivered twice.
        assertNull(tracker.revealed(exposedTop = home))
        assertNull(tracker.revealed(exposedTop = home))
        assertEquals("entry-1", tracker.lastViewedEntryId)
    }

    @Test
    fun `a reveal that exposes an entry already reported earlier in the session is not deduped`() {
        val tracker = AppScreenViewTracker()
        tracker.viewedIfExposed(home, exposedTop = home)
        tracker.viewedIfExposed(detail, exposedTop = detail)
        tracker.revealed(exposedTop = home)

        // home → detail → home → detail → home: every appearance of home counts, because only the
        // IMMEDIATELY preceding report is deduped.
        assertEquals(detail, tracker.viewedIfExposed(detail, exposedTop = detail))
        assertEquals(home, tracker.revealed(exposedTop = home))
    }

    @Test
    fun `a reveal with nothing exposed reports nothing`() {
        val tracker = AppScreenViewTracker()
        tracker.viewedIfExposed(home, exposedTop = home)

        // Both stacks empty — the pop that removed the last entry, before a new presentation.
        assertNull(tracker.revealed(exposedTop = null))
        assertEquals(
            "Nothing was exposed, so the last-reported entry is unchanged",
            "entry-1",
            tracker.lastViewedEntryId
        )
    }

    // endregion

    // region teardown

    @Test
    fun `teardown forgets the last reported entry`() {
        val tracker = AppScreenViewTracker()
        tracker.viewedIfExposed(home, exposedTop = home)

        tracker.reset()

        assertNull(tracker.lastViewedEntryId)
    }

    @Test
    fun `an entry re-established after a teardown is reported again`() {
        val tracker = AppScreenViewTracker()
        tracker.viewedIfExposed(home, exposedTop = home)
        tracker.reset()

        // Rotation: the same URL comes back on a new entry id, and counts again.
        val reestablished = AppScreenView("entry-4", home.screenURL)
        assertEquals(reestablished, tracker.viewedIfExposed(reestablished, exposedTop = reestablished))
    }

    // endregion
}
