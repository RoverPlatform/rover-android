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

package io.rover.sdk.experiences

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import io.rover.sdk.experiences.rich.compose.ui.Environment

/**
 * Display a Rover Experience.
 *
 * Note: the sizing behaviour is to expand to fill all space proposed to the Experience in
 * the Constraints.  Use the Jetpack Compose size(), width(), or height() modifiers to limit
 * the size.
 *
 * @param url The URL of the Experience to display.
 * @param modifier A Jetpack Compose modifier to apply to the Experience.
 * @param navigationMode Controls how the experience integrates with navigation. Defaults to
 * [NavigationMode.Standalone] for backward compatibility. Use [NavigationMode.Pluggable] when
 * embedding in a parent navigation stack (e.g., Hub).
 * @param defaultColorSchemeDark For experiences with color scheme set to auto, should it use dark mode? null to follow system.
 * @param manageStatusBar Whether this experience may style the host window's status bar (icon tint,
 * and background where applicable). Defaults to true. Even when true, the experience only touches the
 * status bar while it is actually drawn as the top-of-screen content; if it is embedded below other
 * chrome it leaves the status bar alone. Set to false when the host owns the status bar itself and
 * the experience should never touch it (e.g. when embedded in the Hub).
 * @param onDismissButtonPressed Pass when presenting the experience full-screen and dismissable; the
 * callback performs the dismissal (e.g. `finish()`). Leave unset when embedding. Only App Screens
 * (Experiences V3) act on it today, adding an explicit close (✕) affordance to the root page; the
 * V1/V2 rendering paths ignore it. The Hub and other embedders leave it null (embedded experiences
 * get no SDK-injected close chrome). By convention a host supplies either this handler (full-screen
 * presenters) or the root affordance (Hub envelope), never both.
 * @param onOpenURL Consulted only for the `openURL` bridge message from an App Screens (V3)
 * experience — never for `presentWebsite`, and not for V1/V2 experiences. When null the SDK opens
 * the URL itself (the Rover deep-link router, falling back to an `ACTION_VIEW` intent).
 */
@Composable
fun ExperienceComposable(
    url: Uri,
    modifier: Modifier = Modifier,
    navigationMode: NavigationMode = NavigationMode.Standalone,
    defaultColorSchemeDark: Boolean? = null,
    manageStatusBar: Boolean = true,
    onDismissButtonPressed: (() -> Unit)? = null,
    onOpenURL: ((Uri) -> Unit)? = null,
) {
    CompositionLocalProvider(Environment.LocalManageStatusBar provides manageStatusBar) {
        Experience(
            url = url,
            modifier = modifier,
            navigationMode = navigationMode,
            defaultColorSchemeDark = defaultColorSchemeDark,
            onDismissButtonPressed = onDismissButtonPressed,
            onOpenURL = onOpenURL
        )
    }
}

/**
 * Binary-compatibility overload retaining the signature published before [onOpenURL] was added, for
 * callers compiled against those versions. New code should call the full-parameter
 * [ExperienceComposable].
 */
@Deprecated(
    message = "Retained for binary compatibility only; use the full-parameter ExperienceComposable.",
    level = DeprecationLevel.HIDDEN,
)
@Composable
fun ExperienceComposable(
    url: Uri,
    modifier: Modifier = Modifier,
    navigationMode: NavigationMode = NavigationMode.Standalone,
    defaultColorSchemeDark: Boolean? = null,
    manageStatusBar: Boolean = true,
    onDismissButtonPressed: (() -> Unit)? = null,
) {
    ExperienceComposable(
        url = url,
        modifier = modifier,
        navigationMode = navigationMode,
        defaultColorSchemeDark = defaultColorSchemeDark,
        manageStatusBar = manageStatusBar,
        onDismissButtonPressed = onDismissButtonPressed,
    )
}

/**
 * Binary-compatibility overload retaining the signature published by SDK &le; 4.14.x, for callers
 * compiled against those versions. New code should call the full-parameter [ExperienceComposable].
 */
@Deprecated(
    message = "Retained for binary compatibility only; use the full-parameter ExperienceComposable.",
    level = DeprecationLevel.HIDDEN,
)
@Composable
fun ExperienceComposable(
    url: Uri,
    modifier: Modifier = Modifier,
    navigationMode: NavigationMode = NavigationMode.Standalone,
    defaultColorSchemeDark: Boolean? = null,
) {
    ExperienceComposable(
        url = url,
        modifier = modifier,
        navigationMode = navigationMode,
        defaultColorSchemeDark = defaultColorSchemeDark,
    )
}
