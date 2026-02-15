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
import androidx.compose.ui.Modifier

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
 */
@Composable
fun ExperienceComposable(
    url: Uri,
    modifier: Modifier = Modifier,
    navigationMode: NavigationMode = NavigationMode.Standalone,
    defaultColorSchemeDark: Boolean? = null,
) {
    Experience(url = url, modifier = modifier, navigationMode = navigationMode, defaultColorSchemeDark = defaultColorSchemeDark)
}
