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
 *
 * The badge indicates how much new content is waiting in the Inbox (Messages): posts delivered
 * since the last time the user looked at the inbox, plus conversations with unread replies.
 *
 * Counts are capped at 9.
 */
interface RoverBadgeInterface {
    /**
     * The badge text to display on the Inbox (Messages) affordance.
     *
     * If null, then the count is 0 and the badge is not displayed. Counts of 10 or more are
     * reported as `"9+"`.
     *
     * Suitable for Jetpack Compose usage with collectAsState().
     */
    val newBadge: StateFlow<String?>

    /**
     * Reactive Streams Publisher API for Java users, in the event that the newBadge StateFlow
     * is not suitable for their use case.
     *
     * Publishes the count of new items in the Inbox (Messages): posts delivered since the user last
     * looked at the inbox, plus conversations with unread replies. The value is capped at 9.
     */
    fun newBadgePublisher(): Publisher<Int>
}
