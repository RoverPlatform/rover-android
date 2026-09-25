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

/**
 * A screen as view-tracking sees it: which entry it is, and the URL to report for it.
 *
 * Deliberately not [NavigatorModel.Entry] — the decisions below need an identity and a URL and
 * nothing else (no session, no template, no Android types), which is what makes them testable on
 * the plain JVM. [AppScreenNavigator] adapts its entries to this on the way in.
 */
internal data class AppScreenView(
    val entryId: String,
    val screenURL: String
)

/**
 * The decision half of `App Screen Viewed`: given what was pushed, what is currently exposed, and
 * what was last reported, decide WHETHER a view should be emitted and FOR WHICH screen.
 *
 * Pulled out of [AppScreenNavigator] because none of it needs a Context, a WebView or a bridge —
 * it is the exposed top's entry id, the pushed id and the last reported id. The navigator keeps the
 * two things that do need the real world: reading the stacks (to supply the exposed top) and
 * emitting the event onto the event queue.
 *
 * Main-thread-only, like the navigator state it mirrors; deliberately not synchronized.
 *
 * The rules, in full:
 *
 * - An EXPOSED push is a view ([viewedIfExposed]). A push that landed behind a presented sheet is
 *   not, and — importantly — does not claim [lastViewedEntryId], so the reveal path can still
 *   report it when the sheet goes away.
 * - A reveal ([revealed]) reports whatever is now exposed, unless that entry is already the last
 *   reported one (a pop reconciliation that removed nothing visible, or was delivered twice).
 * - [reset] forgets the last reported entry, for a teardown that leaves nothing on screen.
 */
internal class AppScreenViewTracker {

    /**
     * The entry a view was last emitted for, or null if none has been (or since the last [reset]).
     * Entry ids are unique per push, so a stale id can never alias a later entry.
     */
    var lastViewedEntryId: String? = null
        private set

    /**
     * [entry] became visible: record it as the last reported one and return it for emission. The
     * unconditional case, used by the two gated paths below.
     */
    fun viewed(entry: AppScreenView): AppScreenView {
        lastViewedEntryId = entry.entryId
        return entry
    }

    /**
     * A push of [entry] happened, with [exposedTop] being what is on screen once it landed: a view
     * when the push IS the exposed top, null when it landed hidden (behind a presented sheet).
     *
     * A push is not always an appearance: the model places a non-`sheet` navigation from a
     * root-stack session onto the root stack even while a sheet is presented, so a `navigate` that
     * arrives from the covered root (a queued message, or a tap that landed as the sheet opened)
     * lands BEHIND the sheet. Reporting it there would record a view the user never saw, and would
     * claim [lastViewedEntryId], suppressing the genuine appearance when the sheet is dismissed.
     */
    fun viewedIfExposed(entry: AppScreenView, exposedTop: AppScreenView?): AppScreenView? {
        if (exposedTop?.entryId != entry.entryId) return null
        return viewed(entry)
    }

    /**
     * A pop / sheet dismissal exposed [exposedTop]: a view for it, or null when nothing is exposed
     * or it is already the entry last reported. This is also what reports a push that landed hidden.
     */
    fun revealed(exposedTop: AppScreenView?): AppScreenView? {
        val top = exposedTop ?: return null
        if (top.entryId == lastViewedEntryId) return null
        return viewed(top)
    }

    /**
     * Forget the last reported entry — the presentation was torn down and nothing is on screen.
     *
     * Not load-bearing on its own (entry ids are unique per push, so the next presentation's root
     * could never be deduped against a dead one anyway); it keeps the tracker's state honest about
     * "nothing has been viewed" rather than carrying an id for a screen that no longer exists.
     */
    fun reset() {
        lastViewedEntryId = null
    }
}
