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

import android.view.View
import androidx.annotation.RestrictTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Whether content whose top edge sits at [topInWindow] (window coordinates) is drawn beneath the
 * status bar, and so owns the status-bar band. This is the shared source of truth for every Rover
 * surface that styles the status bar from its own measured geometry (experience screens via
 * `rememberExperienceStatusBarControl`, the Communication Hub's chrome).
 *
 * Uses the *physical* inset from [ViewCompat.getRootWindowInsets], which reports the real value in
 * both window regimes — unlike the consumed Compose `WindowInsets.statusBars` inset, which reads 0
 * under legacy (opaque-bar) windows because the content is already laid out below the bar.
 *
 * The comparison differs by window regime (see [contentTopInWindow]), and the boundary case is the
 * one that matters:
 *
 *  - **Edge-to-edge**: content is laid out from window y=0, so top-of-screen content has
 *    `topInWindow == 0`. A host that applies `statusBarsPadding()` and embeds this content flush
 *    beneath sits at exactly `statusBarBottom` and must NOT be treated as owning the bar — hence a
 *    strict `<`. Using `<=` here would misjudge the most idiomatic host integration.
 *  - **Legacy**: the decor view is inset, so top-of-screen content already begins at
 *    `statusBarBottom` and `<=` is required to recognise it. This branch is unchanged from the
 *    behaviour that predates the edge-to-edge distinction.
 *
 * This is NOT part of the supported public SDK API surface: it is annotated
 * [RestrictTo][androidx.annotation.RestrictTo] with [LIBRARY_GROUP][RestrictTo.Scope.LIBRARY_GROUP]
 * so that only other Rover modules in this library group (`notifications`, for the Communication
 * Hub's chrome) may call it.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun overlapsStatusBar(view: View, topInWindow: Int): Boolean =
    overlapsStatusBar(
        statusBarBottom = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0,
        contentTopInWindow = contentTopInWindow(view),
        topInWindow = topInWindow,
    )

/**
 * The decision itself, separated from the platform lookups so it can be unit tested — including the
 * legacy branch, which needs a window Android 16 will not produce (it ignores the edge-to-edge
 * opt-out that Android 15 still honours).
 *
 * @param statusBarBottom physical status-bar height in window coordinates; 0 when hidden.
 * @param contentTopInWindow the window offset of `android.R.id.content`. Non-zero means the decor
 * view is inset below an opaque status bar, i.e. a legacy window.
 * @param topInWindow the top edge of the content being tested.
 */
internal fun overlapsStatusBar(
    statusBarBottom: Int,
    contentTopInWindow: Int,
    topInWindow: Int,
): Boolean {
    if (statusBarBottom <= 0) return false
    val legacy = contentTopInWindow > 0
    return if (legacy) topInWindow <= statusBarBottom else topInWindow < statusBarBottom
}

/**
 * The window offset of `android.R.id.content`, which identifies the window regime.
 *
 * Read from the view tree rather than from `WindowInsets.statusBars`: the consumed Compose inset is
 * 0 on the first frame, which is indistinguishable from a legacy window, and that ambiguity lands
 * exactly on the boundary [overlapsStatusBar] decides. The content view's own offset is known as
 * soon as it is laid out — the status-bar height under legacy, 0 under edge-to-edge.
 */
private fun contentTopInWindow(view: View): Int {
    val content = view.rootView?.findViewById<View>(android.R.id.content) ?: return 0
    val location = IntArray(2)
    content.getLocationInWindow(location)
    return location[1]
}
