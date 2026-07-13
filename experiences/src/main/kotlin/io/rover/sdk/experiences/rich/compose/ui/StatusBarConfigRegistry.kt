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

package io.rover.sdk.experiences.rich.compose.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color

/**
 * Registry for status-bar configurations keyed by navigation route.
 *
 * Mirrors [AppBarConfigRegistry], but reported for *every* screen — including screens that have no
 * app bar (for which [AppBarConfig] is null). A pluggable host (e.g. the Hub) that owns the window's
 * status bar looks up the config for the current route to style the status bar with the experience's
 * authored intent.
 */
class StatusBarConfigRegistry {
    private val _configs = mutableStateMapOf<String, StatusBarConfig?>()

    /**
     * Observable map of route to status-bar config.
     */
    val configs: Map<String, StatusBarConfig?> get() = _configs

    /**
     * Register or update the status-bar config for a route.
     */
    fun register(route: String, config: StatusBarConfig?) {
        _configs[route] = config
    }

    /**
     * Get the status-bar config for a specific route.
     */
    fun get(route: String): StatusBarConfig? = _configs[route]

    /**
     * Clear all registered configs.
     */
    fun clear() {
        _configs.clear()
    }
}

/**
 * The authored status-bar styling of an experience screen, reported up to a pluggable host so it can
 * style the window status bar it owns.
 *
 * The values are already resolved against the experience's appearance and the system dark/light mode
 * so the host does not need any knowledge of the experience model:
 *
 * @param lightStatusBarAppearance the value to assign to
 * `WindowInsetsControllerCompat.isAppearanceLightStatusBars` — true for dark icons on a light bar,
 * false for light icons on a dark bar.
 * @param backgroundColor the authored status-bar background colour (distinct from the app-bar
 * background colour).
 */
data class StatusBarConfig(
    val lightStatusBarAppearance: Boolean,
    val backgroundColor: Color,
)

/**
 * CompositionLocal for reporting the status-bar configuration from child screens.
 *
 * Reported for every screen (unlike [LocalAppBarConfigSink], which is app-bar specific), so a host
 * that owns the status bar can style it even for screens that have no app bar.
 *
 * Type: (StatusBarConfig?) -> Unit
 */
val LocalStatusBarConfigSink = compositionLocalOf<((StatusBarConfig?) -> Unit)?> { null }

/**
 * CompositionLocal for providing the status-bar config registry.
 *
 * Parent composables provide this to allow child experiences to register their status-bar configs
 * keyed by route.
 */
val LocalStatusBarConfigRegistry = compositionLocalOf<StatusBarConfigRegistry?> { null }
