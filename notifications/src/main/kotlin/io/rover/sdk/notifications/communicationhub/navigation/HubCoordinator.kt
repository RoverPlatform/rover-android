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

package io.rover.sdk.notifications.communicationhub.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinator for Hub navigation with session-long lifetime.
 * 
 * This coordinator enables deep link navigation to Hub screens before the Hub composable
 * is actually presented on screen. This is useful for coordinating with host app navigation
 * where the Hub might be navigated to a deep link before it's revealed.
 * 
 * Only one Hub is expected in a given app at a given time, so having a singleton
 * coordinator that manages navigation state is appropriate.
 * 
 * Pattern similar to iOS HubCoordinator which promotes NavigationPath to a coordinator
 * with session-long lifetime.
 */
class HubCoordinator {
    private val _pendingNavigation = MutableStateFlow<HubNavigation?>(null)
    
    /**
     * Observable pending navigation state that the Hub composable can collect.
     * 
     * When non-null, indicates a navigation action that should be executed
     * when the Hub is presented. The Hub composable is responsible for consuming
     * this by calling [clearPendingNavigation] after executing the navigation.
     */
    val pendingNavigation: StateFlow<HubNavigation?> = _pendingNavigation.asStateFlow()
    
    /**
     * Navigate to a specific post by ID.
     * 
     * This queues the navigation to be executed when the Hub is presented.
     * The Hub will handle the actual navigation through its NavController,
     * potentially navigating through the inbox first if both home view and
     * inbox are enabled (matching iOS behavior).
     * 
     * @param postId The ID of the post to navigate to
     */
    fun navigateToPost(postId: String) {
        _pendingNavigation.value = HubNavigation.Post(postId)
    }
    
    /**
     * Navigate to the inbox (messages list) screen.
     * 
     * This queues the navigation to be executed when the Hub is presented.
     */
    fun navigateToInbox() {
        _pendingNavigation.value = HubNavigation.Inbox
    }
    
    /**
     * Navigate to the home screen (root of the Hub).
     * 
     * This queues the navigation to be executed when the Hub is presented.
     */
    fun navigateToHome() {
        _pendingNavigation.value = HubNavigation.Home
    }
    
    /**
     * Clears the pending navigation after it has been executed.
     * 
     * The Hub composable should call this after consuming and executing
     * a pending navigation action.
     */
    fun clearPendingNavigation() {
        _pendingNavigation.value = null
    }
}
