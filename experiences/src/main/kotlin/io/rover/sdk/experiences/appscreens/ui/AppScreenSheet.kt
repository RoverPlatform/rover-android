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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.rover.experiences.R
import io.rover.sdk.experiences.appscreens.AppScreenNavigator
import io.rover.sdk.experiences.rich.compose.ui.graphics.enterTransition
import io.rover.sdk.experiences.rich.compose.ui.graphics.exitTransition
import io.rover.sdk.experiences.rich.compose.ui.graphics.popEnterTransition
import io.rover.sdk.experiences.rich.compose.ui.graphics.popExitTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Content height fraction — an iOS pageSheet-like inset below the status bar. */
private const val SHEET_HEIGHT_FRACTION = 0.94f

/**
 * Presents the navigator's sheet stack in a Material3 [ModalBottomSheet] with its OWN [NavHost] and
 * back stack. Navigation inside the sheet pushes within the sheet; a `sheet` transition from inside
 * a sheet is a plain in-sheet push (no sheet-on-sheet) — the placement decision lives in
 * [io.rover.sdk.experiences.appscreens.NavigatorModel].
 *
 * The sheet is fully expanded (`skipPartiallyExpanded`), handle-less, and edge-to-edge; its content
 * occupies [SHEET_HEIGHT_FRACTION] of the height for the pageSheet look. Swipe-down / scrim tap /
 * back at the sheet root all reach [ModalBottomSheet]'s `onDismissRequest`, which releases the whole
 * sheet stack (ephemerals destroyed, template sessions left warm). When the inner NavHost has a back
 * stack it consumes back itself (an in-sheet pop); only at its root does back fall through to the
 * sheet's own dismissal handling.
 *
 * Native chrome floats over the sheet's NavHost in the safe-area top band, crossfading as depth
 * changes so exactly one capsule shows: a close (✕) button (TopEnd) on the sheet root, and a back
 * button (TopStart) on in-sheet pushes (iOS parity — the xmark lives on the root and pushing swaps
 * it for back). Close runs the M3 hide animation before releasing the stack; back pops the sheet's
 * own NavHost, reconciling via [StackSync] like a swipe/back. Because every sheet entry always
 * carries chrome, the sheet folds [OverlayAffordanceTopBand] into its injected top inset
 * unconditionally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScreenSheet(navigator: AppScreenNavigator) {
    val master = navigator.sheetStack.firstOrNull() ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // The sheet card never underlaps top system UI (it starts below the status bar/cutout at
    // 1 - SHEET_HEIGHT_FRACTION of the height), so its safe area has no top system contribution:
    // exclude the top from safeDrawing. One expression feeds BOTH the hosts' inset injection and the
    // chrome capsules' padding so the injected band and the capsule geometry cannot disagree — the
    // injected top becomes exactly OverlayAffordanceTopBand, and the capsules sit exactly centered in
    // it. Horizontal is kept for landscape cutouts; bottom keeps the real nav-bar inset.
    val sheetWindowInsets = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

    ModalBottomSheet(
        onDismissRequest = { navigator.onSheetDismissed() },
        sheetState = sheetState,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SHEET_HEIGHT_FRACTION)
                // The sheet card sits below the status bar, so consume the status-bar top inset for
                // descendant windowInsetsPadding-family modifiers (e.g. inside the host's error/retry
                // UI), which would otherwise double-pad for system UI the card already clears. NOTE:
                // this does NOT narrow composition-level WindowInsets reads — the hosts' injected
                // insets and the chrome capsules are instead fed sheetWindowInsets explicitly (above).
                .consumeWindowInsets(WindowInsets.statusBars)
        ) {
            key(master.entryId) {
                val navController = rememberNavController()
                StackSync(
                    navController = navController,
                    entryIds = { navigator.sheetStack.map { it.entryId } },
                    routePrefix = APP_SCREEN_SHEET_ROUTE,
                    onPopped = navigator::onSheetPopped
                )

                NavHost(
                    navController = navController,
                    startDestination = "$APP_SCREEN_SHEET_ROUTE/${master.entryId}",
                    enterTransition = { enterTransition() },
                    exitTransition = { exitTransition() },
                    popEnterTransition = { popEnterTransition() },
                    popExitTransition = { popExitTransition() }
                ) {
                    composable("$APP_SCREEN_SHEET_ROUTE/{$APP_SCREEN_ENTRY_ARG}") { backStackEntry ->
                        val entryId = backStackEntry.arguments?.getString(APP_SCREEN_ENTRY_ARG)
                        // Capture the session ONCE per entry (see AppScreensRoot's route for the full
                        // rationale): the in-sheet pop has the identical ~300ms slide, and a live
                        // sessionFor read would go null at pop-commit and flash the sheet background.
                        val session = remember(entryId) { entryId?.let { navigator.sessionFor(it) } }
                        // Signal composition disposal so the navigator destroys a deferred ephemeral
                        // then, not at pop-commit. Covers both the in-sheet pop and full sheet
                        // dismissal (the ModalBottomSheet leaving composition disposes every entry).
                        // Same shared pending-disposal map / onEntryDisposed as the root NavHost.
                        DisposableEffect(entryId) {
                            onDispose { entryId?.let(navigator::onEntryDisposed) }
                        }
                        if (session != null && entryId != null) {
                            AppScreenHost(
                                session = session,
                                onRetry = { navigator.retry(entryId) },
                                onLazyRecover = { navigator.lazyRecover(entryId) },
                                // Top-excluded: the injected top is the chrome band alone (72px).
                                windowInsets = sheetWindowInsets,
                                // Every sheet entry always carries chrome (✕ on the root, back on
                                // pushes), so fold the overlay band into every sheet document's top
                                // inset unconditionally — the page always lays out clear of the capsule.
                                additionalTopInset = OverlayAffordanceTopBand
                            )
                        }
                    }
                }

                // Overlay the sheet's native chrome on top of its NavHost. Extracted to a BoxScope
                // extension so it composes against the content Box's scope (for .align) without the
                // ModalBottomSheet content's ambient ColumnScope shadowing AnimatedVisibility.
                SheetChrome(
                    navigator = navigator,
                    navController = navController,
                    sheetState = sheetState,
                    scope = scope,
                    windowInsets = sheetWindowInsets
                )
            }
        }
    }
}

/**
 * The sheet's native chrome, crossfading over the 300ms push/pop slide so exactly one capsule shows:
 * a close (✕) button (TopEnd) on the sheet root and a back button (TopStart) on in-sheet pushes (iOS
 * parity — the xmark lives on the root; pushing swaps it for back). Reading [navigator]'s `sheetStack`
 * size (a [androidx.compose.runtime.snapshots.SnapshotStateList]) drives the crossfade reactively.
 *
 * A [BoxScope] extension so it composes against the sheet content Box's scope — `.align` is available
 * and the [ModalBottomSheet] content's ambient `ColumnScope` no longer shadows [AnimatedVisibility].
 *
 * [windowInsets] is the sheet's top-excluded safe area — the same insets the hosts inject — so the
 * capsules sit exactly [OverlayAffordanceTopBand]-centered below the sheet's top edge, in agreement
 * with the pages' injected band.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.SheetChrome(
    navigator: AppScreenNavigator,
    navController: NavHostController,
    sheetState: SheetState,
    scope: CoroutineScope,
    windowInsets: WindowInsets
) {
    AnimatedVisibility(
        visible = navigator.sheetStack.size == 1,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.align(Alignment.TopEnd)
    ) {
        AppScreenOverlayButton(
            icon = Icons.Default.Close,
            badgeText = null,
            contentDescription = stringResource(R.string.rover_app_screen_close_button),
            // Dismiss WITH the M3 slide-down: hide() animates the sheet away, then the completion
            // releases the stack. Calling onSheetDismissed() directly would empty sheetStack and yank
            // the sheet from composition with no animation.
            onTap = {
                scope.launch { sheetState.hide() }
                    .invokeOnCompletion { if (!sheetState.isVisible) navigator.onSheetDismissed() }
            },
            windowInsets = windowInsets
        )
    }
    AnimatedVisibility(
        visible = navigator.sheetStack.size > 1,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.align(Alignment.TopStart)
    ) {
        AppScreenOverlayButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            badgeText = null,
            contentDescription = stringResource(R.string.rover_app_screen_back_button),
            // Pop the sheet's own NavHost; the guard makes rapid double-taps harmless and the pop
            // reconciles through StackSync -> navigator.onSheetPopped.
            onTap = {
                if (navController.previousBackStackEntry != null) navController.popBackStack()
            },
            windowInsets = windowInsets
        )
    }
}
