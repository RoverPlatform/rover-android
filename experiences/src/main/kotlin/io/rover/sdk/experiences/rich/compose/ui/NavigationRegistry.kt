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

package io.rover.sdk.experiences.rich.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController

/**
 * Registry for dynamically registering navigation destinations from nested composables.
 * 
 * This enables inversion of control - child composables can register their screens
 * into a parent NavHost without the parent knowing about the child's internal structure.
 */
class NavDestinationRegistry {
    private val _destinations: SnapshotStateList<RegisteredDestination> = mutableStateListOf()
    
    /**
     * Observable list of registered destinations. NavHost can observe this to reactively
     * pick up new registrations.
     */
    val destinations: List<RegisteredDestination> get() = _destinations
    
    /**
     * Register a new destination. Duplicates (by route) are ignored.
     */
    fun register(route: String, content: @Composable () -> Unit) {
        if (_destinations.none { it.route == route }) {
            _destinations.add(RegisteredDestination(route, content))
        }
    }
    
    /**
     * Clear all registered destinations.
     */
    fun clear() {
        _destinations.clear()
    }
}

/**
 * A registered navigation destination consisting of a route and composable content.
 */
data class RegisteredDestination(
    val route: String,
    val content: @Composable () -> Unit
)

/**
 * Registry for app bar configurations keyed by navigation route.
 * 
 * Enables declarative app bar management where screens register their configs
 * and the parent looks up the config for the current route.
 */
class AppBarConfigRegistry {
    private val _configs = mutableStateMapOf<String, AppBarConfig?>()
    
    /**
     * Observable map of route to app bar config.
     */
    val configs: Map<String, AppBarConfig?> get() = _configs
    
    /**
     * Register or update app bar config for a route.
     * Pass null to indicate no app bar for this route.
     */
    fun register(route: String, config: AppBarConfig?) {
        _configs[route] = config
    }
    
    /**
     * Get the app bar config for a specific route.
     */
    fun get(route: String): AppBarConfig? = _configs[route]
    
    /**
     * Clear all registered configs.
     */
    fun clear() {
        _configs.clear()
    }
}

/**
 * Configuration for an app bar extracted from an experience screen.
 * 
 * @param title The title content to display
 * @param navigationIcon The navigation icon (typically back button), if any
 * @param backgroundColor The background color of the app bar
 * @param actions The action items to display in the app bar
 * @param isRootScreen True if this is the root (initial) screen of the experience
 */
data class AppBarConfig(
    val title: @Composable () -> Unit,
    val navigationIcon: @Composable (() -> Unit)?,
    val backgroundColor: Color,
    val actions: List<@Composable () -> Unit>,
    val isRootScreen: Boolean
)

/**
 * CompositionLocal for providing the navigation destination registry.
 * 
 * Parent composables provide this to allow child experiences to register
 * their screens into the parent's NavHost.
 */
val LocalNavDestinationRegistry = compositionLocalOf<NavDestinationRegistry?> { null }

/**
 * CompositionLocal for providing an external NavHostController.
 * 
 * When present, experiences use this controller instead of creating their own,
 * enabling integration with parent navigation stacks.
 */
val LocalExternalNavController = compositionLocalOf<NavHostController?> { null }

/**
 * CompositionLocal for reporting app bar configuration from child screens.
 * 
 * Each screen reports its app bar config when it becomes active, allowing
 * the parent to render a unified app bar combining experience and parent actions.
 * 
 * Type: (AppBarConfig?) -> Unit
 */
val LocalAppBarConfigSink = compositionLocalOf<((AppBarConfig?) -> Unit)?> { null }

/**
 * CompositionLocal for providing the app bar config registry.
 * 
 * Parent composables provide this to allow child experiences to register
 * their app bar configs keyed by route.
 */
val LocalAppBarConfigRegistry = compositionLocalOf<AppBarConfigRegistry?> { null }
