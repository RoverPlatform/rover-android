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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.rover.experiences.R
import io.rover.sdk.experiences.appscreens.AppScreenNavigator
import io.rover.sdk.experiences.appscreens.LocalAppScreenRootAffordance
import io.rover.sdk.experiences.appscreens.rememberAppScreenDarkTheme
import io.rover.sdk.experiences.rich.compose.ui.graphics.enterTransition
import io.rover.sdk.experiences.rich.compose.ui.graphics.exitTransition
import io.rover.sdk.experiences.rich.compose.ui.graphics.popEnterTransition
import io.rover.sdk.experiences.rich.compose.ui.graphics.popExitTransition

internal const val APP_SCREEN_ROOT_ROUTE = "appScreenRoot"
internal const val APP_SCREEN_SHEET_ROUTE = "appScreenSheet"
internal const val APP_SCREEN_ENTRY_ARG = "entryId"

/**
 * The navigable root of an App Screens presentation.
 *
 * A [NavHost] with a single parameterized route renders each entry's [AppScreenHost] by looking up
 * its session on the navigator. The navigator's `rootStack` (a Compose [androidx.compose.runtime.snapshots.SnapshotStateList])
 * is the source of truth: pushes flow FROM the stack TO the NavHost, and pops flow BACK from the
 * NavHost's committed back stack to the navigator. The bottom sheet, when present, hosts its own
 * independent NavHost.
 *
 * The NavHost is keyed on the master entry id so a fresh presentation (a new `start`) rebuilds it
 * cleanly against the new root.
 *
 * Native chrome floats over the NavHost in the safe-area top band ([OverlayAffordanceTopBand]): an
 * optional host-supplied affordance capsule (TopEnd) on the root entry, an optional close (✕) capsule
 * (TopEnd) on the root entry when the presenter supplies [onDismissButtonPressed] (full-screen,
 * dismissable), and a back capsule (TopStart) on every pushed entry. The back button is owned by App
 * Screens itself — it applies to every presentation regardless of host — and pops through the NavHost's
 * own back stack, reconciling via [StackSync] exactly as the system/predictive back gesture does. The
 * close button crossfades with root depth (matching the sheet's TopEnd close button and the back
 * button's TopStart crossfade — it is visible at the root and fades out as a detail is pushed), while
 * the host affordance still appears/disappears instantly with depth; the back button crossfades with
 * the push/pop slide. Close (TopEnd) and back (TopStart) no longer share a corner, so both crossfade
 * without underlapping. By convention a host supplies either the dismiss handler or the TopEnd
 * affordance, never both, so those two TopEnd capsules never collide.
 *
 * [active] is false when another surface currently owns the singleton navigator's presentation (a
 * second App Screens surface was presented over this one). An inactive surface renders only a neutral
 * background and binds no host, so it never contends for the shared WebView; it rebinds when it
 * becomes active again (the navigator re-establishes it — see [AppScreenNavigator.release]).
 *
 * [onDismissButtonPressed] is non-null only for a full-screen, dismissable presentation; its callback
 * performs the dismissal and drives the root-page close (✕) button. Embedded surfaces pass null and
 * get no close chrome.
 */
@Composable
internal fun AppScreensRoot(
    navigator: AppScreenNavigator,
    active: Boolean = true,
    modifier: Modifier = Modifier,
    onDismissButtonPressed: (() -> Unit)? = null
) {
    // Config-aware appearance (DARK/LIGHT force; AUTO/null follows the device), shared by the neutral
    // placeholder background and the standalone status-bar icon appearance below, so both agree with
    // the WebView content and the Hub chrome.
    val resolvedDark = rememberAppScreenDarkTheme()

    // Standalone (full-screen, dismissable) presentation only: the deep-link ExperienceActivity's
    // edge-to-edge status bar follows the SYSTEM theme, so a config-forced-dark page would keep dark
    // status-bar icons on a near-black background (and vice versa). Drive the icon appearance from the
    // resolved scheme, restoring the window's previous value on dispose. Embedded (Hub) surfaces pass
    // a null dismiss handler and are left untouched — they own their window and set their own
    // appearance. Guarded on a resolvable host Activity/window.
    if (onDismissButtonPressed != null) {
        val context = LocalContext.current
        DisposableEffect(resolvedDark, context) {
            val window = context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            val previousLightStatusBars = controller?.isAppearanceLightStatusBars
            controller?.isAppearanceLightStatusBars = !resolvedDark
            onDispose {
                if (controller != null && previousLightStatusBars != null) {
                    controller.isAppearanceLightStatusBars = previousLightStatusBars
                }
            }
        }
    }

    // A device whose installed System WebView lacks the WEB_MESSAGE_LISTENER feature cannot host the
    // App Screen bridge, so the navigator can build no session at all and gates [present] before
    // touching its model (see [AppScreenNavigator.bridgeUnsupported]). There is therefore no session
    // whose phase could carry an [AppScreenPhase.Error], so the load-error visual is answered here at
    // the presentation level — reusing the same [ErrorState] the per-screen failure shows. Retry
    // re-runs the (static per-device) capability gate; it recovers only if the WebView gains the
    // feature, but keeps the affordance consistent with every other App Screens failure.
    if (navigator.bridgeUnsupported.value) {
        val background = if (resolvedDark) Color(0xFF0B0B0B) else Color(0xFFF1F2F4)
        Box(modifier = modifier.fillMaxSize().background(background)) {
            ErrorState(dark = resolvedDark, onRetry = navigator::retryPresent)
        }
        return
    }

    if (!active) {
        val background = if (resolvedDark) Color(0xFF0B0B0B) else Color(0xFFF1F2F4)
        Box(modifier = modifier.fillMaxSize().background(background))
        return
    }
    val master = navigator.rootStack.firstOrNull() ?: return

    key(master.entryId) {
        val navController = rememberNavController()
        StackSync(
            navController = navController,
            entryIds = { navigator.rootStack.map { it.entryId } },
            routePrefix = APP_SCREEN_ROOT_ROUTE,
            onPopped = navigator::onPopped
        )

        // Native host-supplied affordance (the Hub inbox action), or null (no host provided one, e.g.
        // standalone deep-link). Read once here: it drives the TopEnd affordance capsule on the root
        // and — keyed on being PROVIDED, not on visibility — folds its band into the ROOT screen's
        // injected top inset (see below). Pushed screens fold the band unconditionally for the back
        // button, so this only affects the root.
        val affordance = LocalAppScreenRootAffordance.current

        Box(modifier = modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "$APP_SCREEN_ROOT_ROUTE/${master.entryId}",
                modifier = Modifier.fillMaxSize(),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() }
            ) {
                composable("$APP_SCREEN_ROOT_ROUTE/{$APP_SCREEN_ENTRY_ARG}") { backStackEntry ->
                    val entryId = backStackEntry.arguments?.getString(APP_SCREEN_ENTRY_ARG)
                    // Capture the session ONCE per entry rather than reading navigator.sessionFor on
                    // every recomposition. The exiting entry must keep rendering through the ~300ms
                    // pop-exit slide, but a live sessionFor read goes null at pop-commit (onPopped
                    // removes the entry from rootStack the instant the pop commits, while this host
                    // stays composed for the whole transition) — recomposing to nothing exposed the
                    // white window background (the pop white flash). The entry's session always exists
                    // at first composition (StackSync navigates only after the entry is on the stack),
                    // so remember keeps the still-attached WebView and Revealed phase visible through
                    // the transition; AndroidView.onRelease still detaches at disposal.
                    val session = remember(entryId) { entryId?.let { navigator.sessionFor(it) } }
                    // Signal composition disposal (the pop-exit transition finished) so the navigator
                    // can destroy a deferred ephemeral WebView then — not at pop-commit, which would
                    // blank the exiting screen. The model's pending-disposal map is shared across both
                    // NavHosts, so this one call covers root and sheet pops alike. No timers.
                    DisposableEffect(entryId) {
                        onDispose { entryId?.let(navigator::onEntryDisposed) }
                    }
                    if (session != null && entryId != null) {
                        AppScreenHost(
                            session = session,
                            onRetry = { navigator.retry(entryId) },
                            onLazyRecover = { navigator.lazyRecover(entryId) },
                            // A floating overlay capsule occupies the top band on this entry; fold that
                            // band into the injected top inset so the page lays out clear of the button
                            // (the Android counterpart of iOS's nav bar contributing to the safe area).
                            // Keyed on the entry's ROLE and the chrome being PROVIDED, not on any button's
                            // momentary visibility, so each document's inset stays stable across push/pop
                            // (see [appScreenEntryTopInset]).
                            additionalTopInset = appScreenEntryTopInset(
                                isRootEntry = entryId == master.entryId,
                                hasHostAffordance = affordance != null,
                                isDismissable = onDismissButtonPressed != null
                            )
                        )
                    }
                }
            }

            // The host affordance is overlaid in the safe-area top band on the ROOT entry only. Reading
            // navigator.rootStack.size (a SnapshotStateList) here reactively hides it the instant a
            // detail is pushed and restores it on pop; a presented sheet is a separate window that
            // naturally covers it. It appears and disappears instantly (no fade) as it always trades
            // places with the back button. Not shown in the sheet's own NavHost.
            if (affordance != null && navigator.rootStack.size == 1) {
                AppScreenOverlayButton(
                    icon = affordance.icon,
                    badgeText = affordance.badgeText,
                    contentDescription = affordance.contentDescription,
                    onTap = affordance.onTap,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // The close (✕) button is overlaid on the ROOT entry only when the presenter supplied a
            // dismiss handler (full-screen, dismissable). It sits at TopEnd — matching the sheet's
            // close button and iOS — and crossfades with root depth (visible when size == 1, fading
            // out as a detail is pushed and back in on pop), the exact mirror of the back button's
            // TopStart crossfade. Because close now lives at TopEnd (not TopStart) it no longer shares
            // a corner with the back button, so a crossfade is safe here (the old code deliberately
            // used an instant gate to avoid the two underlapping at TopStart). The host affordance
            // also lives at TopEnd, but by documented convention a host supplies either this dismiss
            // handler or the TopEnd affordance, never both, so they cannot collide.
            if (onDismissButtonPressed != null) {
                AnimatedVisibility(
                    visible = navigator.rootStack.size == 1,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    AppScreenOverlayButton(
                        icon = Icons.Default.Close,
                        badgeText = null,
                        contentDescription = stringResource(R.string.rover_app_screen_close_button),
                        onTap = onDismissButtonPressed
                    )
                }
            }

            // The back button is overlaid at TopStart on every PUSHED root-stack entry (rootStack.size
            // > 1, the exact mirror of the affordance's and close button's == 1 gates) and is owned by
            // App Screens itself, so it applies to every presentation regardless of whether a host
            // affordance is present. It owns the TopStart corner alone — the close button now lives at
            // TopEnd — and crossfades over the 300ms push/pop slide. Tapping pops the NavHost's own
            // back stack; the
            // guard makes rapid double-taps harmless, and the pop reconciles through StackSync ->
            // navigator.onPopped, the same path as the system/predictive back gesture.
            AnimatedVisibility(
                visible = navigator.rootStack.size > 1,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                AppScreenOverlayButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    badgeText = null,
                    contentDescription = stringResource(R.string.rover_app_screen_back_button),
                    onTap = {
                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                    }
                )
            }
        }
    }

    if (navigator.sheetStack.isNotEmpty()) {
        AppScreenSheet(navigator = navigator)
    }
}

/**
 * Converges a [navController]'s committed back stack with a navigator stack of [entryIds].
 *
 * Pushes: any id present in the navigator stack (beyond the start destination) but not yet requested
 * is navigated to. Pops: any id that WAS observed committed in the NavHost but has since left the
 * committed back stack is reported via [onPopped].
 *
 * Predictive-back cancellation is handled for free: `currentBackStack` reflects only COMMITTED
 * entries, so a cancelled back gesture never drops an id from it and never triggers [onPopped]; only
 * a completed pop does. The `everSeen` gate additionally prevents the push window (a freshly-added
 * id that the NavController has not yet committed) from being misread as a pop.
 */
@Composable
internal fun StackSync(
    navController: NavHostController,
    entryIds: () -> List<String>,
    routePrefix: String,
    onPopped: (List<String>) -> Unit
) {
    val requested = remember { mutableSetOf<String>() }
    val everSeen = remember { mutableSetOf<String>() }

    // Push: drive the NavHost from the navigator stack.
    LaunchedEffect(navController) {
        snapshotFlow { entryIds() }.collect { ids ->
            requested.retainAll(ids.toSet())
            val present = navController.currentBackStack.value
                .mapNotNull { it.arguments?.getString(APP_SCREEN_ENTRY_ARG) }
                .toSet()
            // Skip the master (start destination); navigate the rest that aren't there yet.
            ids.drop(1).forEach { id ->
                if (id !in present && id !in requested) {
                    requested.add(id)
                    navController.navigate("$routePrefix/$id")
                }
            }
        }
    }

    // Pop: reconcile the navigator stack back from the committed NavHost back stack.
    LaunchedEffect(navController) {
        navController.currentBackStack.collect { backStack ->
            val present = backStack
                .mapNotNull { it.arguments?.getString(APP_SCREEN_ENTRY_ARG) }
                .toSet()
            everSeen.addAll(present)
            val removed = entryIds().filter { it in everSeen && it !in present }
            if (removed.isNotEmpty()) {
                everSeen.removeAll(removed.toSet())
                onPopped(removed)
            }
        }
    }
}

/** The padding between the window safe area and a floating overlay capsule button. */
private val RootAffordancePadding = 12.dp

/** The diameter of a floating overlay capsule button container. */
private val RootAffordanceSize = 48.dp

/**
 * The horizontal padding between the safe-area edge and an overlay capsule: wider than the vertical
 * [RootAffordancePadding] because the Material 3 [Badge] overhangs the 48dp container's top-end corner and
 * would otherwise crowd the screen edge (matches the iOS bar item's bumped horizontal padding).
 */
private val RootAffordanceHorizontalPadding = 20.dp

/**
 * The top band a floating overlay capsule occupies below the window safe area — shared by every
 * App Screens overlay button (the host root affordance, the pushed-screen back button, and the
 * sheet's close/back chrome). The capsule sits vertically centered between the window inset and the
 * page content's safe-area edge, with [RootAffordancePadding] of breathing room above and below —
 * matching iOS nav-bar spacing, where the capsule is centered in the nav-bar band. This is folded
 * into a screen's injected `--rover-safe-area-inset-top` so pages lay out clear of the capsule, the
 * Android counterpart of iOS's nav bar contributing to the safe area. Derived from the capsule's own
 * layout constants so the two cannot drift.
 */
internal val OverlayAffordanceTopBand: Dp = RootAffordancePadding + RootAffordanceSize + RootAffordancePadding

/**
 * The additional top inset a root-stack entry folds into its injected `--rover-safe-area-inset-top`,
 * contributing [OverlayAffordanceTopBand] whenever the entry carries overlay chrome so the page lays
 * out clear of the capsule. Keyed on the entry's ROLE and the chrome being PROVIDED — not on any
 * button's momentary visibility — so a document's inset stays stable across push/pop and never reflows
 * as a capsule fades in or out: a pushed entry ([isRootEntry] false) always carries the back button;
 * the root entry carries a capsule only when a host affordance ([hasHostAffordance]) or a dismiss
 * handler ([isDismissable]) is provided.
 */
internal fun appScreenEntryTopInset(
    isRootEntry: Boolean,
    hasHostAffordance: Boolean,
    isDismissable: Boolean
): Dp = when {
    !isRootEntry -> OverlayAffordanceTopBand
    hasHostAffordance || isDismissable -> OverlayAffordanceTopBand
    else -> 0.dp
}

/**
 * A floating overlay capsule button: a circular, slightly-elevated, semi-opaque container (the
 * Android stand-in for iOS's liquid-glass capsule) holding [icon] as a Material3 [IconButton], with
 * a [Badge] when [badgeText] is non-null. Shared by all App Screens chrome — the host root
 * affordance, the pushed-screen back button, and the sheet's close/back buttons — so the capsules
 * cannot drift apart visually.
 *
 * It sits in the safe-area top band via [windowInsets] padding — the full [WindowInsets.safeDrawing]
 * by default, aligning with the same inset values App Screens injects into the document — so it
 * reads below the status bar/cutout and inside the horizontal safe area regardless of the page
 * content beneath it. A caller whose surface never underlaps some system UI passes the same narrowed
 * insets it hands `AppScreenHost` (the sheet excludes the top), keeping capsule and injected band in
 * exact agreement. The container is pinned to [RootAffordanceSize] (rather than relying on Material 3's
 * implicit minimum interactive size, which could drift across Material versions) so
 * [OverlayAffordanceTopBand] stays exact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScreenOverlayButton(
    icon: ImageVector,
    badgeText: String?,
    contentDescription: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing
) {
    BadgedBox(
        badge = {
            badgeText?.let { text ->
                Badge { Text(text) }
            }
        },
        modifier = modifier
            .windowInsetsPadding(windowInsets)
            .padding(vertical = RootAffordancePadding, horizontal = RootAffordanceHorizontalPadding)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            modifier = Modifier.size(RootAffordanceSize)
        ) {
            IconButton(onClick = onTap) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription
                )
            }
        }
    }
}
