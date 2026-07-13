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

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.rover.sdk.experiences.appscreens.ui.AppScreensRoot
import io.rover.sdk.experiences.appscreens.ui.findActivity
import io.rover.sdk.experiences.rich.compose.ui.Services

/**
 * The App Screens (Experiences V3) shell root.
 *
 * Entry-point composable for AI-authored HTML App Screens served at `/a/…` URLs. It is intentionally
 * thin: it resolves the singleton [AppScreenNavigator], starts it at [url] (resetting any prior
 * presentation while preserving warm sessions) and tears it down when it leaves composition, then
 * hands off to [AppScreensRoot] which renders the navigable stack (a NavHost plus an optional
 * Material3 bottom sheet). The navigator owns every WebView, session, and load pipeline.
 *
 * [onDismissButtonPressed] is non-null when this surface is presented full-screen and dismissable
 * (its callback performs the dismissal); it drives the root page's close (✕) affordance. Embedded
 * surfaces leave it null and get no close chrome.
 */
@Composable
internal fun AppScreen(
    url: Uri,
    modifier: Modifier = Modifier,
    onDismissButtonPressed: (() -> Unit)? = null
) {
    // Deep links arrive with the app's custom scheme (rv-myapp://<associated-domain>/a/…);
    // normalize to https before the navigator consumes the URL literally (bridge origin rules,
    // the `.json` data fetch, href resolution) — mirrors iOS
    // (ExperienceViewController.loadAppScreensExperience) and the V1/V2 fetch pipeline. The host
    // gate has already run by this point (Experience.kt), and it matches on host alone.
    val screenUrl = remember(url) { AppScreensDecisions.normalizeScheme(url) }

    Services.Inject { services ->
        val navigator = services.appScreenNavigator
        val context = LocalContext.current

        // A stable per-composition token identifying THIS App Screens surface to the singleton
        // navigator. Two surfaces can be composed at once (e.g. a standalone presentation over the
        // still-composed Hub home tab); the token lets the navigator track which surface currently
        // owns the shared presentation and re-establish the covered one when this surface leaves.
        val surfaceToken = remember { Any() }

        // The colour-scheme override the HOST applies to THIS surface, mapped to a forced-dark
        // tri-state (Hub-only policy: the Hub carries its config colorScheme here; a standalone
        // full-screen presentation leaves the local unset → null → follow the device). The navigator
        // no longer reads the config itself; the value is threaded to it per surface below.
        val forcedDark = forcedDark(LocalAppScreenColorSchemeOverride.current)

        DisposableEffect(surfaceToken, screenUrl) {
            // Record the host decor view for the DecorAttached prewarm strategy hedge.
            navigator.setCurrentDecorView(
                context.findActivity()?.window?.decorView as? ViewGroup
            )
            navigator.present(surfaceToken, screenUrl, forcedDark)
            onDispose {
                navigator.release(surfaceToken)
                navigator.setCurrentDecorView(null)
            }
        }

        // Apply a LIVE override change (a Hub config flip while this surface stays composed) without
        // re-running present(): [forcedDark] must NOT be a key of the DisposableEffect above, or the
        // navigation stack would reset on every appearance change. This dedicated effect pushes the
        // new value through updateSurfaceAppearance instead; on first composition it no-ops because
        // present() already carried the same value (the navigator dedupes unchanged appearances).
        LaunchedEffect(forcedDark) {
            navigator.updateSurfaceAppearance(surfaceToken, forcedDark)
        }

        // Only the surface the navigator currently regards as active binds the shared WebView; a
        // covered surface renders a neutral placeholder so it never contends for the WebView.
        val active = navigator.activeSurfaceToken.value === surfaceToken
        AppScreensRoot(
            navigator = navigator,
            active = active,
            modifier = modifier,
            onDismissButtonPressed = onDismissButtonPressed
        )
    }
}

/**
 * The phase surfaced to the App Screen host for its reveal/skeleton/error logic. The gray→green
 * phase *banner* is drawn by the HTML runtime itself; this native phase only governs whether the
 * WebView is hidden, revealed, or replaced by the error state.
 */
internal sealed interface AppScreenPhase {
    /** Pre-reveal: the WebView is loading and the first `show` has not yet resolved. */
    object Loading : AppScreenPhase

    /** The first `show` resolved; the WebView is revealed (the morph may still land after). */
    object Revealed : AppScreenPhase

    /** A failure or timeout occurred; the host shows the error state with retry. */
    data class Error(val throwable: Throwable) : AppScreenPhase
}
