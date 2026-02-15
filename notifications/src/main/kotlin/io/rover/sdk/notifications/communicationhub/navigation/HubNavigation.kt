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

/**
 * Represents navigation destinations within the Hub.
 * 
 * This sealed class enables type-safe navigation commands that can be queued
 * before the Hub composable is presented on screen, enabling deep link coordination
 * with host app navigation.
 */
sealed class HubNavigation {
    /**
     * Navigate to a specific post detail screen.
     * 
     * @param postId The ID of the post to display
     */
    data class Post(val postId: String) : HubNavigation()
    
    /**
     * Navigate to the inbox (messages list) screen.
     */
    object Inbox : HubNavigation()
    
    /**
     * Navigate to the home screen (root of the Hub).
     */
    object Home : HubNavigation()
}
