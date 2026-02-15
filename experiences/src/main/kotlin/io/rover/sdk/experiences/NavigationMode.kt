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

/**
 * Defines how an Experience integrates with navigation.
 */
sealed class NavigationMode {
    /**
     * Standalone mode - the experience manages its own internal navigation stack using
     * its own NavHost and NavController. This is the default behavior and matches the
     * current implementation.
     * 
     * In this mode:
     * - Experience creates its own NavHost
     * - Screens have their own AppBar via Scaffold
     * - Navigation is self-contained
     * - Suitable for full-screen experiences or fragments
     */
    object Standalone : NavigationMode()
    
    /**
     * Pluggable mode - the experience integrates with an external navigation stack.
     * 
     * In this mode:
     * - Experience registers its screens into parent's NavHost via NavDestinationRegistry
     * - Root screen is rendered inline; child screens are registered as separate destinations
     * - Each screen reports its AppBar config to parent via LocalAppBarConfigSink
     * - Parent controls the unified AppBar, combining experience and parent actions
     * - Navigation uses external NavController from LocalExternalNavController
     * 
     * This mode enables:
     * - Seamless integration with parent navigation stacks
     * - Merged app bars on root screen (experience + parent actions)
     * - Deep link support that properly clears experience child screens
     * 
     * Suitable for embedded experiences like Hub home view.
     */
    object Pluggable : NavigationMode()
}
