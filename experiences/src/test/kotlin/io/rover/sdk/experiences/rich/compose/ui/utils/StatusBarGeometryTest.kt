/*
 * Copyright (c) 2026, Rover Labs, Inc. All rights reserved.
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

package io.rover.sdk.experiences.rich.compose.ui.utils

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test

/**
 * Covers the shared rule deciding whether a surface owns the status-bar band — used by both
 * experience screens ([rememberExperienceStatusBarControl]) and the Communication Hub's chrome.
 *
 * Every value below was measured on a device rather than chosen, and the two regimes come from
 * different devices — which also confirms nothing here is tied to one screen's geometry:
 *
 *  - **Edge-to-edge**: Pixel 9 Pro, Android 16, 480dpi. Status-bar inset 156px, content laid out
 *    from window y=0.
 *  - **Legacy**: Android 15, 420dpi, with `windowOptOutEdgeToEdgeEnforcement` (honoured on 15,
 *    ignored on 16). Status-bar inset 63px, and `android.R.id.content` offset 63px — i.e. equal to
 *    the inset, which is what [overlapsStatusBar] relies on to identify the regime.
 */
class StatusBarGeometryTest {
    /** Edge-to-edge device: Pixel 9 Pro @ 480dpi. */
    private val statusBar = 156

    /** Legacy device: Android 15 @ 420dpi. Content begins at the inset, so this doubles as its offset. */
    private val legacyStatusBar = 63

    // --- Edge-to-edge: content is laid out from window y=0, so contentTopInWindow is 0. ---

    @Test
    fun `edge to edge full screen content owns the status bar`() {
        assertThat(
            overlapsStatusBar(statusBarBottom = statusBar, contentTopInWindow = 0, topInWindow = 0),
            equalTo(true)
        )
    }

    @Test
    fun `edge to edge content flush below the inset does not own the status bar`() {
        // The host applied statusBarsPadding() and embedded flush beneath it. The boundary case:
        // a non-strict comparison here would hand the surface a band that is not its own. (The
        // experiences renderer previously used <= here, wrongly claiming the bar in exactly this
        // layout.)
        assertThat(
            overlapsStatusBar(statusBarBottom = statusBar, contentTopInWindow = 0, topInWindow = statusBar),
            equalTo(false)
        )
    }

    @Test
    fun `edge to edge content below host chrome does not own the status bar`() {
        assertThat(
            overlapsStatusBar(statusBarBottom = statusBar, contentTopInWindow = 0, topInWindow = 300),
            equalTo(false)
        )
    }

    @Test
    fun `edge to edge content partway into the inset still owns the status bar`() {
        assertThat(
            overlapsStatusBar(statusBarBottom = statusBar, contentTopInWindow = 0, topInWindow = 80),
            equalTo(true)
        )
    }

    // --- Legacy: the decor view is inset, so top-of-screen content begins at the status-bar height. ---

    @Test
    fun `legacy top of screen content owns the status bar`() {
        // Measured: statusBarBottom=63 contentTopInWindow=63 topInWindow=63. Top-of-screen content
        // begins *at* the inset here, which is why this branch must not use a strict comparison.
        assertThat(
            overlapsStatusBar(
                statusBarBottom = legacyStatusBar,
                contentTopInWindow = legacyStatusBar,
                topInWindow = legacyStatusBar
            ),
            equalTo(true)
        )
    }

    @Test
    fun `legacy content below host chrome does not own the status bar`() {
        // Measured: topInWindow=189, the inset plus the host's 48dp header at 420dpi.
        assertThat(
            overlapsStatusBar(
                statusBarBottom = legacyStatusBar,
                contentTopInWindow = legacyStatusBar,
                topInWindow = 189
            ),
            equalTo(false)
        )
    }

    // --- No status bar at all. ---

    @Test
    fun `hidden status bar is never owned`() {
        assertThat(
            overlapsStatusBar(statusBarBottom = 0, contentTopInWindow = 0, topInWindow = 0),
            equalTo(false)
        )
    }

    @Test
    fun `hidden status bar is never owned even in a legacy window`() {
        assertThat(
            overlapsStatusBar(
                statusBarBottom = 0,
                contentTopInWindow = legacyStatusBar,
                topInWindow = legacyStatusBar
            ),
            equalTo(false)
        )
    }
}
