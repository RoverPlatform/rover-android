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

import io.rover.sdk.core.events.domain.Event

/**
 * The App Screens analytics events and the pure builders that shape them.
 *
 * Both events carry FLAT attributes (the shape the Communication Hub's `Post Opened` /
 * `Post Link Clicked` events use), deliberately NOT the nested, `campaignID`-bearing shape the V2
 * Experiences emitter uses: an App Screen has no campaign and no server-visible screen ID, so its
 * identity here is URL-only. The URLs are the WHOLE payload — anything derivable from them (a
 * template path, which kind of link was tapped) is left to the consumer rather than duplicated on
 * the wire.
 *
 * Emission lives in [AppScreenNavigator] — the one place that knows which entry is on screen and
 * which bridge message carried a tap. These builders are kept separate so the event names and
 * attribute keys are unit-testable without a WebView.
 */

/** The namespace every App Screens event is tracked under (its own BigQuery table). */
internal const val APP_SCREEN_EVENT_NAMESPACE = "rover"

internal const val APP_SCREEN_VIEWED_EVENT_NAME = "App Screen Viewed"
internal const val APP_SCREEN_LINK_CLICKED_EVENT_NAME = "App Screen Link Clicked"

/**
 * `App Screen Viewed`: the screen at [screenURL] became visible — on its first appearance and again
 * whenever it is revealed by a pop back onto it.
 *
 * [screenURL] is the absolute URL the navigator actually uses for the entry, query included and with
 * the internal `roverPrewarm` switch already stripped.
 */
internal fun appScreenViewedEvent(screenURL: String): Event = Event(
    name = APP_SCREEN_VIEWED_EVENT_NAME,
    attributes = mapOf("screenURL" to screenURL)
)

/**
 * `App Screen Link Clicked`: a link on the screen at [screenURL] was tapped, resolving to [linkURL].
 *
 * Which bridge message carried the tap (`navigate` / `openURL` / `presentWebsite`) is deliberately
 * NOT reported — it is a navigator-internal implementation detail, and it appears only in the
 * emission's `log.d` line.
 */
internal fun appScreenLinkClickedEvent(
    screenURL: String,
    linkURL: String
): Event = Event(
    name = APP_SCREEN_LINK_CLICKED_EVENT_NAME,
    attributes = mapOf(
        "screenURL" to screenURL,
        "linkURL" to linkURL
    )
)
