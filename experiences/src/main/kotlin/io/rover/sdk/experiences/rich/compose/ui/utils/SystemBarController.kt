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

package io.rover.sdk.experiences.rich.compose.ui.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.rover.sdk.experiences.rich.compose.model.values.Appearance
import io.rover.sdk.experiences.rich.compose.model.values.StatusBarStyle

@Composable
internal fun rememberSystemBarController(
    window: Window? = findWindow()
): SystemBarController =
    remember(window) { SystemBarController(window) }

@Composable
private fun findWindow(): Window? =
    (LocalView.current.parent as? DialogWindowProvider)?.window
        ?: LocalView.current.context.findWindow()

/**
 * Controls the host window's status-bar icon tint (light vs dark icons) on behalf of an experience.
 *
 * One instance is created per experience (provided through
 * [io.rover.sdk.experiences.rich.compose.ui.Environment.LocalSystemBarController]) and shared by all
 * of that experience's screens. A screen calls [apply] while its top edge is actually drawn beneath
 * the status bar and on screen, and [release] otherwise.
 *
 * The first [apply] snapshots the host window's existing tint so it can be restored once no screen
 * is active any longer. This is what prevents the tint change from "sticking" on the host after the
 * experience is dismissed or scrolled away. Per-screen applied state (rather than a single flag)
 * keeps the tint stable across navigation transitions, where an outgoing and incoming screen are
 * briefly both present: the baseline is only restored when the last screen releases, and if the
 * newest owner releases first the next-newest owner is reapplied.
 *
 * Status-bar *background* colour is handled in two ways depending on the regime, and the caller
 * decides which by passing [legacyBackgroundColor]:
 *  - Under edge-to-edge enforcement (Android 15+, or any host that opted in) `Window.statusBarColor`
 *    is deprecated and ignored, and the standalone chrome (the `Scaffold` top bar) already paints the
 *    status-bar background, so the caller passes `null` here.
 *  - In the legacy (opaque status bar) regime the caller passes the authored colour and we set
 *    `Window.statusBarColor`, snapshotting and restoring it exactly like the tint.
 */
internal class SystemBarController(
    private val window: Window?
) {
    private val appliedStates = LinkedHashMap<String, AppliedState>()
    private var savedAppearanceLightStatusBars: Boolean? = null
    private var savedStatusBarColor: Int? = null
    private var pendingRestore: Runnable? = null

    /**
     * Apply [statusBarStyle]'s icon tint (and, when [legacyBackgroundColor] is non-null, the legacy
     * `Window.statusBarColor`) to the host window on behalf of [screenId], snapshotting the host's
     * original values on the first call so they can later be restored.
     */
    fun apply(
        screenId: String,
        statusBarStyle: StatusBarStyle,
        appearance: Appearance,
        legacyBackgroundColor: Int? = null
    ) {
        val window = window ?: return
        // A screen is taking ownership again; cancel any pending baseline restore so a screen-to-screen
        // navigation handoff does not briefly flash the host baseline (see [release]).
        cancelPendingRestore()
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (savedAppearanceLightStatusBars == null) {
            savedAppearanceLightStatusBars = controller.isAppearanceLightStatusBars
        }
        if (legacyBackgroundColor != null && savedStatusBarColor == null) {
            @Suppress("DEPRECATION")
            savedStatusBarColor = window.statusBarColor
        }
        val appliedState = AppliedState(statusBarStyle, appearance, legacyBackgroundColor)
        appliedStates.remove(screenId)
        appliedStates[screenId] = appliedState
        applyToWindow(window, appliedState)
    }

    /**
     * Release [screenId]. Once no screens remain active, restore the host's original tint and (if it
     * was changed) status-bar background colour.
     *
     * The restore is deferred briefly rather than applied immediately. During an in-experience
     * navigation the outgoing screen releases before the incoming screen applies (there is a gap, not
     * an overlap, because the incoming screen only becomes active once it is laid out and visible), so
     * restoring synchronously would flash the host baseline for the duration of the transition. The
     * deferred restore is cancelled by the incoming [apply], so it only actually runs when the
     * experience is genuinely done (dismissed, or scrolled off with nothing taking over).
     */
    fun release(screenId: String) {
        val window = window ?: return
        appliedStates.remove(screenId) ?: return
        if (appliedStates.isEmpty()) {
            scheduleRestore(window)
        } else {
            applyToWindow(window, appliedStates.entries.last().value)
        }
    }

    private fun applyToWindow(window: Window, appliedState: AppliedState) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars =
            appliedState.statusBarStyle.isDarkIconTint(window.context, appliedState.appearance)
        val legacyBackgroundColor = appliedState.legacyBackgroundColor
        if (legacyBackgroundColor != null) {
            @Suppress("DEPRECATION")
            window.statusBarColor = legacyBackgroundColor
        } else {
            savedStatusBarColor?.let { original ->
                @Suppress("DEPRECATION")
                window.statusBarColor = original
            }
        }
    }

    private fun scheduleRestore(window: Window) {
        cancelPendingRestore()
        val restore = Runnable {
            pendingRestore = null
            // Re-check: an incoming screen may have taken ownership while we were waiting.
            if (appliedStates.isNotEmpty()) return@Runnable
            savedAppearanceLightStatusBars?.let { original ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = original
            }
            savedStatusBarColor?.let { original ->
                @Suppress("DEPRECATION")
                window.statusBarColor = original
            }
            savedAppearanceLightStatusBars = null
            savedStatusBarColor = null
        }
        pendingRestore = restore
        window.decorView.postDelayed(restore, HANDOFF_RESTORE_DELAY_MS)
    }

    private fun cancelPendingRestore() {
        pendingRestore?.let { window?.decorView?.removeCallbacks(it) }
        pendingRestore = null
    }

    private companion object {
        // Comfortably longer than the in-experience navigation animation (300ms) so a screen-to-screen
        // handoff never restores the host baseline between the outgoing release and the incoming apply.
        const val HANDOFF_RESTORE_DELAY_MS = 400L
    }

    private data class AppliedState(
        val statusBarStyle: StatusBarStyle,
        val appearance: Appearance,
        val legacyBackgroundColor: Int?
    )
}

/**
 * Whether this style should result in dark status-bar icons. `isAppearanceLightStatusBars` is set to
 * this value: a "light" status bar background calls for dark icons, and vice versa.
 */
internal fun StatusBarStyle.isDarkIconTint(context: Context, appearance: Appearance): Boolean =
    when (this) {
        StatusBarStyle.DEFAULT -> !context.isDarkMode(appearance)
        StatusBarStyle.LIGHT -> false
        StatusBarStyle.DARK -> true
        StatusBarStyle.INVERTED -> context.isDarkMode(appearance)
    }

private tailrec fun Context.findWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findWindow()
        else -> null
    }

private fun Context.isDarkMode(appearance: Appearance): Boolean =
    when (appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        Appearance.AUTO -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
