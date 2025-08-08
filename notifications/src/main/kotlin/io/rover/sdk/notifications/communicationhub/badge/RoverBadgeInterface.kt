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

package io.rover.sdk.notifications.communicationhub.badge

import kotlinx.coroutines.flow.StateFlow
import org.reactivestreams.Publisher

/**
 * Provides badge count information that can be observed in Jetpack Compose or traditional Views.
 * The badge indicates the number of unread Inbox (Communication Hub) posts.
 */
interface RoverBadgeInterface {
    /**
     * Whether the Inbox (Communication Hub) tab has unread items and should display a badge.
     *
     * If null, then the count is 0 and the badge is not displayed.
     *
     * Suitable for Jetpack Compose usage with collectAsState().
     */
    val newBadge: StateFlow<String?>
    
    /**
     * Reactive Streams Publisher API for Java users, in the event that the newBadge StateFlow
     * is not suitable for their use case.
     *
     * Publishes the unread count of posts in the Inbox (Communication Hub).
     */
    fun newBadgePublisher(): Publisher<Int>
}
