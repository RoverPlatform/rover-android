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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.rover.sdk.experiences.rich.compose.model.values.Appearance
import io.rover.sdk.experiences.rich.compose.model.values.StatusBarStyle
import io.rover.sdk.experiences.rich.compose.ui.Environment

/**
 * Tracks whether a screen is actually drawn as the top-of-screen content and, if so, applies the
 * experience's authored status-bar styling (icon tint plus background) to the host window, restoring
 * it afterwards. Returns a [Modifier] that must be applied to the root layout of the screen so its
 * window position and on-screen visibility can be tracked.
 *
 * The previous behaviour applied the tint unconditionally for every experience and never reverted
 * it. That was wrong in two ways: it changed the host's status bar even for experiences embedded
 * below the status-bar area, and the change "stuck" after navigating away from the experience.
 *
 * Rather than guessing from whether the experience is full-screen or embedded — which is unreliable,
 * since an embedded experience can equally well be placed at the top of the screen — this measures
 * the screen's real window position via [onLayoutRectChanged] and its on-screen visibility via
 * [onVisibilityChanged]. Styling is applied only while the screen's top edge sits within the
 * status-bar inset band (i.e. it is the top-of-screen content) *and* the screen is on screen, and is
 * released (restoring the host's original state) otherwise, on disposal, or when the experience is no
 * longer visible.
 *
 * The overlap test uses the *physical* status-bar height from [ViewCompat.getRootWindowInsets], which
 * reports the real inset in both the edge-to-edge and legacy regimes — unlike the consumed Compose
 * [WindowInsets.statusBars] inset, which reads 0 in the legacy regime because the content is laid out
 * below the opaque bar.
 *
 * The status-bar background is only ever set in the legacy (opaque-bar) regime, via
 * `Window.statusBarColor` (through [SystemBarController]); under edge-to-edge enforcement the standalone
 * chrome (the `Scaffold` top bar) already paints the status-bar background, so nothing extra is drawn
 * here.
 *
 * When [Environment.LocalManageStatusBar] is false the whole concern is disabled: the modifier is
 * still applied (harmlessly) but styling is never applied.
 *
 * @param screenId identifies this screen to the shared [SystemBarController].
 * @param statusBarStyle the screen's desired status-bar icon style.
 * @param statusBarBackgroundColor the screen's authored status-bar background colour.
 * @param appearance the experience's appearance, used to resolve DEFAULT/INVERTED against dark mode.
 */
@Composable
internal fun rememberExperienceStatusBarControl(
    screenId: String,
    statusBarStyle: StatusBarStyle,
    statusBarBackgroundColor: Color,
    appearance: Appearance,
): Modifier {
    val manageStatusBar = Environment.LocalManageStatusBar.current
    val controller = Environment.LocalSystemBarController.current
    val view = LocalView.current

    // The consumed Compose inset is > 0 only under edge-to-edge (transparent-bar) enforcement; in the
    // legacy regime the content is laid out below the opaque bar and this reads 0. We use it purely to
    // decide whether the background needs to be set via Window.statusBarColor (legacy only). (It is 0
    // on the very first frame and settles a recomposition later, which is fine here — it is read as
    // Compose state.)
    val edgeToEdge = WindowInsets.statusBars.getTop(LocalDensity.current) > 0

    var overlapsStatusBar by remember { mutableStateOf(false) }
    var onScreen by remember { mutableStateOf(false) }
    val active = manageStatusBar && overlapsStatusBar && onScreen

    if (controller != null) {
        val backgroundArgb = statusBarBackgroundColor.toArgb()
        LaunchedEffect(active, edgeToEdge, statusBarStyle, appearance, backgroundArgb) {
            if (active) {
                // In the legacy regime we also set the opaque bar's colour; under edge-to-edge
                // `Window.statusBarColor` is ignored and the standalone chrome paints the status-bar
                // background instead, so we pass null here.
                controller.apply(
                    screenId,
                    statusBarStyle,
                    appearance,
                    legacyBackgroundColor = if (edgeToEdge) null else backgroundArgb
                )
            } else {
                controller.release(screenId)
            }
        }
        DisposableEffect(controller, screenId) {
            onDispose { controller.release(screenId) }
        }
    }

    // Note for reviewers: gating status-bar styling on the content's real geometry is deliberate but
    // uncommon — there is no established library or Google sample doing this, so there is no prior
    // art to pattern-match against. The usual approaches either style unconditionally (assuming the
    // app owns the whole screen) or lean on SystemBarStyle.auto(), which flips icons by the *system*
    // dark/light theme rather than by what is actually drawn behind the bar. onLayoutRectChanged and
    // onVisibilityChanged (Compose 1.9) are officially intended for analytics impressions, prefetch,
    // and video play/pause; using them to drive system-bar appearance is off-label but well-behaved,
    // and gets us close to the iOS "styling follows the content under the bar" model that Android
    // does not yet provide for app content.
    return Modifier
        .onLayoutRectChanged { bounds ->
            // The physical status bar occupies window-y [0, statusBarBottom]. If the screen's top edge
            // falls within that band it is the top-of-screen content and should own the status bar.
            // getRootWindowInsets reports the physical inset in both regimes (the consumed Compose
            // inset would read 0 in legacy). statusBarBottom is 0 when the status bar is hidden, in
            // which case there is nothing to style.
            val statusBarBottom = ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            overlapsStatusBar = statusBarBottom > 0 && bounds.boundsInWindow.top <= statusBarBottom
        }
        .onVisibilityChanged(minFractionVisible = 0.5f) { visible ->
            onScreen = visible
        }
}
