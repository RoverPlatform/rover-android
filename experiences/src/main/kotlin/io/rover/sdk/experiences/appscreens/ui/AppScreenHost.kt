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

package io.rover.sdk.experiences.appscreens.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.rover.sdk.experiences.appscreens.AppScreenInsets
import io.rover.sdk.experiences.appscreens.AppScreenPhase
import io.rover.sdk.experiences.appscreens.AppScreenSession
import io.rover.sdk.experiences.appscreens.rememberAppScreenDarkTheme
import io.rover.sdk.experiences.appscreens.withForcedNightModeTheme
import kotlinx.coroutines.delay

private const val SKELETON_GRACE_MS = 300L

/**
 * The phased host for a single App Screen [session].
 *
 * Renders a full-size, scheme-aware background (matching the runtime's page background so there is
 * never a white flash in dark mode) behind a [FrameLayout] container into which the session's
 * [android.webkit.WebView] is attached as a child. The container indirection is a deliberate
 * navigation invariant: a warm WebView moves between navigation slots, so on every attach the
 * WebView is first unconditionally removed from any previous parent (a fast re-push may leave it
 * still parented to a disposing slot). `onRelease` only detaches — the navigator owns destruction.
 *
 * At attach the WebView's [MutableContextWrapper] base is swapped to the host Activity (so an
 * attached WebView has a real window-token context); at detach it is swapped back to the session's
 * neutral themed context to avoid leaking the Activity into the warm pool.
 *
 * The WebView stays hidden (alpha 0) until [AppScreenPhase.Revealed]; a minimal pulsing skeleton
 * appears only after a 300ms grace while still loading — laid out under the same [windowInsets] +
 * [additionalTopInset] geometry injected into the document, so its bars clear the status bar / chrome
 * band; [AppScreenPhase.Error] shows a centered retry affordance.
 *
 * The host injects [windowInsets] (converted to CSS px) plus [additionalTopInset] into the document
 * as the `--rover-safe-area-inset-*` custom properties. [windowInsets] defaults to the full
 * [WindowInsets.safeDrawing]; a caller whose surface never underlaps some system UI passes a
 * narrowed set instead (the sheet excludes the top — see `AppScreenSheet`). Note that ancestor
 * `consumeWindowInsets` does NOT narrow this composition-level read; only the parameter does.
 */
@Composable
internal fun AppScreenHost(
    session: AppScreenSession,
    onRetry: () -> Unit,
    onLazyRecover: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    additionalTopInset: Dp = 0.dp
) {
    val dark = rememberAppScreenDarkTheme()
    val background = if (dark) Color(0xFF0B0B0B) else Color(0xFFF1F2F4)

    val currentPhase by session.phase
    val webView = session.webView
    val revealed = currentPhase is AppScreenPhase.Revealed
    val context = LocalContext.current

    // The Activity-backed context the WebView's MutableContextWrapper is swapped to on attach, with
    // the session's forced night-mode bits AND a DayNight theme applied. The theme is load-bearing:
    // Chromium resolves `prefers-color-scheme` through the WebView's CURRENT context, so a bare
    // forced ConfigurationContext here (which drops the theme, whose default resolves isLightTheme
    // light) snaps an attached WebView back to light regardless of the forced construction context
    // — see withForcedNightModeTheme. Computed once per (Activity, forced scheme): the wrapper is a
    // FRESH instance each call for a forced scheme, so it must be remembered rather than rebuilt in
    // the update lambda (which would re-wrap on every recomposition). A config flip rebuilds the
    // WebView (session.webView is Compose state), recomposing this host and re-reading the session's
    // now-updated forced scheme. Null when no Activity is resolvable yet (pre-attach). For AUTO/null
    // the wrapper returns the Activity unchanged, preserving the pre-override attach behaviour.
    val attachContext = remember(context, session.forcedDark) {
        context.findActivity()?.withForcedNightModeTheme(session.forcedDark)
    }

    // Lazy recovery: a session marked dead while off-screen recovers the moment it becomes
    // visible again. This host is composed only for the visible entry, so re-entering composition
    // after a pop-to / sheet dismissal — i.e. this effect running with dead == true — is exactly the
    // "became visible" signal. The navigator's lazyRecover is idempotent against repeat calls.
    val isDead = session.dead.value
    LaunchedEffect(session, isDead) {
        if (isDead) onLazyRecover()
    }

    // Publish this host's safe-area insets to the document as CSS custom properties: the WebView
    // renders edge-to-edge, so env(safe-area-inset-*) is 0 and pages' top padding would otherwise
    // collapse under the status bar. [windowInsets] (safeDrawing by default — status/navigation bars
    // and the display cutout; the sheet passes a top-excluded set since its card never underlaps top
    // system UI) is converted device px → CSS px (÷ density). [additionalTopInset] adds the floating
    // overlay-capsule band to the top edge so pages lay out clear of the overlaid button (0 when no
    // chrome floats over this screen). Re-runs on every (re)attach and whenever the insets change.
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = AppScreenInsets.fromDevicePx(
        topPx = windowInsets.getTop(density),
        rightPx = windowInsets.getRight(density, layoutDirection),
        bottomPx = windowInsets.getBottom(density),
        leftPx = windowInsets.getLeft(density, layoutDirection),
        density = density.density,
        additionalTopDp = additionalTopInset.value
    )
    LaunchedEffect(session, insets) {
        session.injectInsets(insets)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (revealed) 1f else 0f),
            factory = { factoryContext ->
                FrameLayout(factoryContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { container ->
                if (container.getChildAt(0) !== webView) {
                    // Unconditionally detach from any prior slot before re-attaching here.
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    container.removeAllViews()
                    container.addView(
                        webView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }
                // Give the attached WebView an Activity-backed context (window token), carrying the
                // session's forced night-mode bits. Compared by identity to the remembered
                // [attachContext] so it re-bases only when the Activity/scheme actually changed (a
                // fresh ConfigurationContext each recomposition would otherwise thrash the wrapper).
                (webView.context as? MutableContextWrapper)?.let { wrapper ->
                    if (attachContext != null && wrapper.baseContext !== attachContext) {
                        wrapper.baseContext = attachContext
                    }
                }
            },
            onRelease = { container ->
                // Detach only; never destroy (the navigator owns the WebView lifecycle).
                (webView.parent as? ViewGroup)?.removeView(webView)
                container.removeAllViews()
                // Swap back to the neutral themed context so the warm pool holds no Activity.
                (webView.context as? MutableContextWrapper)?.baseContext = session.appThemedContext
            }
        )

        when (currentPhase) {
            is AppScreenPhase.Error -> ErrorState(dark = dark, onRetry = onRetry)
            // The skeleton must respect the same geometry the WebView content gets ([windowInsets] +
            // [additionalTopInset]); otherwise its bars render under the status bar / chrome band.
            // ErrorState is centered, so it needs no inset.
            is AppScreenPhase.Loading -> Skeleton(
                dark = dark,
                windowInsets = windowInsets,
                additionalTopInset = additionalTopInset
            )
            is AppScreenPhase.Revealed -> Unit
        }
    }
}

/** Resolve an [Activity] from a Compose [Context], unwrapping [ContextWrapper]s. Null if none. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * A minimal pulsing skeleton, shown only after the [SKELETON_GRACE_MS] grace elapses.
 *
 * Lays out under the SAME safe-area geometry the WebView content receives — [windowInsets] followed
 * by [additionalTopInset] (the floating overlay-capsule band), applied BEFORE the cosmetic 24dp
 * padding — so the bars never render under the status bar / chrome band (iOS parity). The two insets
 * are padded separately (rather than reading [WindowInsets.safeDrawing] here) because the sheet feeds
 * a top-excluded [windowInsets] plus [additionalTopInset] == [OverlayAffordanceTopBand]; passing them
 * through explicitly lands the skeleton correctly in the sheet too.
 */
@Composable
private fun Skeleton(dark: Boolean, windowInsets: WindowInsets, additionalTopInset: Dp) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SKELETON_GRACE_MS)
        visible = true
    }
    if (!visible) return

    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton-pulse"
    )
    val barColor = if (dark) Color(0xFF2A2A2C) else Color(0xFFD8DADE)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets)
            .padding(top = additionalTopInset)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val widths = listOf(0.6f, 0.9f, 0.75f, 0.85f)
        widths.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(18.dp)
                    .alpha(pulse)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor)
            )
        }
    }
}

/** The failure state: a short message and a retry button. */
@Composable
internal fun ErrorState(dark: Boolean, onRetry: () -> Unit) {
    val textColor = if (dark) Color(0xFFECECEC) else Color(0xFF1A1A1A)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Couldn't load this screen.", color = textColor)
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Retry")
        }
    }
}
