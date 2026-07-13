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

package io.rover.sdk.notifications.communicationhub.conversations

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Visibility state of a conversation displayed via the standalone fallback path
 * ([ConversationStandaloneFallback] in [ShowConversationActivity]).
 */
internal sealed class StandaloneConversationVisibilityState {
    object Hidden : StandaloneConversationVisibilityState()

    data class ShowingConversation(val conversationId: String) : StandaloneConversationVisibilityState()
}

/**
 * Tracks which conversation (if any) is currently visible in the standalone fallback path.
 * Consumed by [AndroidConversationPushNotificationPresenter] to suppress notifications when
 * the user is already viewing the conversation in [ShowConversationActivity].
 */
internal class StandaloneConversationVisibilityTracker {
    private val _visibilityState = MutableStateFlow<StandaloneConversationVisibilityState>(
        StandaloneConversationVisibilityState.Hidden
    )

    val visibilityState: StateFlow<StandaloneConversationVisibilityState> = _visibilityState.asStateFlow()

    fun updateConversationVisibility(conversationId: String, isVisible: Boolean) {
        if (isVisible) {
            _visibilityState.value = StandaloneConversationVisibilityState.ShowingConversation(conversationId)
        } else if (_visibilityState.value ==
            StandaloneConversationVisibilityState.ShowingConversation(conversationId)
        ) {
            _visibilityState.value = StandaloneConversationVisibilityState.Hidden
        }
    }
}
