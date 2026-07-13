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

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.RestrictTo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import io.rover.sdk.core.data.config.CommHubColorScheme

/**
 * Resolves the App Screens appearance from a HOST-PROVIDED per-surface colorScheme override
 * ([LocalAppScreenColorSchemeOverride]) and applies it to the contexts a WebView resolves
 * `prefers-color-scheme` from.
 *
 * The override is NOT read from the remote config by App Screens: it is a Hub-only policy declared
 * by the host. The Hub provides its config colorScheme around its embedded home experience, while a
 * standalone full-screen presentation leaves the local unset and follows the device — mirroring iOS,
 * where a presented surface inherits its presenter's resolved traits rather than reading the config.
 * The value's upstream, in the Hub, is the config colorScheme; here it arrives per surface.
 *
 * App Screen documents style their dark appearance through the CSS `prefers-color-scheme` media
 * query. On targetSdk 33+ WebView resolves that query from the `android:isLightTheme` attribute of
 * the theme of its CURRENT context (falling back to that context's `Configuration.uiMode` when the
 * theme omits it) — never from the surrounding Compose theme, and, device-proven, not permanently
 * from the construction context either: Chromium re-evaluates at least on window attach, through
 * whatever the [android.content.MutableContextWrapper]'s base is at that moment. So to honour a host
 * override, EVERY context the WebView resolves through — the construction context (governs the
 * detached phases: prewarm, warm pool, initial load) and the attach-time swapped-in Activity
 * context — must carry both the forced night-mode bits and a DayNight theme.
 *
 * This mirrors the mechanism the Hub post WebView uses (notifications'
 * `communicationhub.rememberCommHubDarkTheme` + PostDetail's forced-uiMode context). The resolver is
 * duplicated here against the shared core primitives rather than reused because `notifications`
 * depends on `experiences`, not the reverse — `experiences` must not depend on the notifications
 * module.
 */

/**
 * The config colorScheme override the HOST applies to this App Screens surface, or null (the
 * default) when the surface follows the device appearance.
 *
 * The override is Hub-only policy: the Hub provides its config colorScheme here around its embedded
 * home experience, while standalone full-screen presentations leave it unset and follow the device —
 * mirroring iOS, where a presented surface inherits its presenter's resolved traits. Tri-state on
 * purpose: AUTO must stay "no override" (null forcedDark) so the neutral path keeps following device
 * configuration natively rather than being pinned to a snapshot of it.
 *
 * The [AppScreen] host reads this local, maps it through [forcedDark], and threads the result to the
 * navigator per surface; a live change (a Hub config flip while composed) recomposes with the new
 * value and is pushed to the navigator without resetting the navigation stack. Cross-module hook
 * exposed to sibling Rover modules only via [RestrictTo][androidx.annotation.RestrictTo]
 * ([LIBRARY_GROUP][RestrictTo.Scope.LIBRARY_GROUP]); NOT part of the supported public SDK API surface.
 */
@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public val LocalAppScreenColorSchemeOverride = compositionLocalOf<CommHubColorScheme?> { null }

/**
 * Map a config colorScheme override to whether App Screens should be forced dark:
 * [CommHubColorScheme.DARK] → `true`, [CommHubColorScheme.LIGHT] → `false`,
 * [CommHubColorScheme.AUTO] or `null` (absent) → `null`, meaning "follow the device".
 *
 * The tri-state return is deliberate: `null` is NOT "light" but "no override", so callers can keep
 * today's byte-identical device-following behaviour for AUTO instead of pinning the scheme.
 */
internal fun forcedDark(scheme: CommHubColorScheme?): Boolean? = when (scheme) {
    CommHubColorScheme.DARK -> true
    CommHubColorScheme.LIGHT -> false
    CommHubColorScheme.AUTO, null -> null
}

/**
 * Return a [Context] whose `Configuration.uiMode` has its night-mode bits forced to match
 * [forcedDark], or this context unchanged when [forcedDark] is `null` (AUTO/no override — the
 * device's appearance is left to propagate untouched, so nothing about the neutral path changes).
 *
 * When forced, [UI_MODE_NIGHT_MASK] bits are masked out and replaced with
 * [Configuration.UI_MODE_NIGHT_YES]/[Configuration.UI_MODE_NIGHT_NO] on a copy of the current
 * configuration, then handed to [Context.createConfigurationContext]. The forced bits live on the
 * configuration itself, so they survive a later system configuration change dispatched through the
 * view tree — the override sticks even when the device theme flips underneath.
 *
 * IMPORTANT: the returned context has DROPPED its theme — `createConfigurationContext` yields a
 * bare context whose default theme resolves `android:isLightTheme` light, and WebView prefers that
 * theme attribute over the uiMode fallback. A context a WebView will ever resolve
 * `prefers-color-scheme` through must layer a DayNight theme on top: the factory wraps this in its
 * own `ContextThemeWrapper`; attach-time swaps must use [withForcedNightModeTheme] instead.
 */
internal fun Context.withForcedNightMode(forcedDark: Boolean?): Context {
    if (forcedDark == null) return this
    return createConfigurationContext(forcedNightConfiguration(forcedDark))
}

/**
 * Return a [Context] suitable for the WebView's attach-time [android.content.MutableContextWrapper]
 * base swap: this context (the host Activity) with [forcedDark]'s night bits applied AND a
 * DayNight theme preserved, or this context unchanged when [forcedDark] is `null`.
 *
 * Why not [withForcedNightMode]: Chromium re-evaluates `prefers-color-scheme` through the WebView's
 * CURRENT context, not just the construction context (verified empirically via CDP: detached warm
 * sessions — whose wrapper base is the DayNight-themed construction context — resolve the forced
 * scheme, while a session whose base was swapped to a bare `createConfigurationContext` result
 * resolves light). The bare context drops the theme, and its default theme's
 * `android:isLightTheme` (light) beats the forced uiMode fallback. So the attach context wraps the
 * Activity in a [ContextThemeWrapper] over [android.R.style.Theme_DeviceDefault_DayNight] — the
 * same theme as the construction context — with the forced configuration applied via
 * [ContextThemeWrapper.applyOverrideConfiguration] (before first resources access, per its
 * contract). The Activity remains the wrapper's base, so window-token/IME behaviour — the reason
 * the attach swap exists — is unaffected.
 */
internal fun Context.withForcedNightModeTheme(forcedDark: Boolean?): Context {
    if (forcedDark == null) return this
    // Compute the override from THIS context before touching the wrapper: reading the wrapper's own
    // resources first would trip applyOverrideConfiguration's "resources already accessed" check.
    val configuration = forcedNightConfiguration(forcedDark)
    return android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_DayNight)
        .apply { applyOverrideConfiguration(configuration) }
}

/** A copy of this context's current configuration with the night bits forced to [forcedDark]. */
private fun Context.forcedNightConfiguration(forcedDark: Boolean): Configuration {
    val nightMode = if (forcedDark) {
        Configuration.UI_MODE_NIGHT_YES
    } else {
        Configuration.UI_MODE_NIGHT_NO
    }
    return Configuration(resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
    }
}

/**
 * Resolve whether the App Screens native chrome (host background, skeleton, error state, root
 * placeholder) should render dark from the HOST-PROVIDED per-surface override
 * ([LocalAppScreenColorSchemeOverride]): DARK/LIGHT force the appearance, AUTO (or an absent value —
 * the standalone default) follows the device via [isSystemInDarkTheme].
 *
 * The composable mirror of [forcedDark]: where [forcedDark] returns `null` for "follow the device",
 * this collapses that to the live [isSystemInDarkTheme] value so native chrome and the WebView's
 * forced-uiMode content always agree. In the Hub the local carries the config colorScheme, so an
 * embedded App Screen stays in lockstep with the Hub's own `rememberCommHubDarkTheme` (which drives
 * the surrounding Material theme); in a standalone presentation the local defaults to null → pure
 * device following, which is exactly the policy.
 */
@Composable
internal fun rememberAppScreenDarkTheme(): Boolean {
    return when (val forced = forcedDark(LocalAppScreenColorSchemeOverride.current)) {
        null -> isSystemInDarkTheme()
        else -> forced
    }
}
