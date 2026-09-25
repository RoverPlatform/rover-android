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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.rover.sdk.core.logging.log

/**
 * Requests that the latest reply be marked read only while the user can actually see it: the
 * host lifecycle is at least STARTED and the thread is near its bottom. Re-evaluated whenever
 * the latest reply, the bottom-proximity, or the lifecycle state changes, so a reply deferred
 * by either gate is marked the moment it is genuinely seen.
 *
 * The same reply can be requested more than once (for example on every return to the bottom);
 * deduplication belongs to [markRead]'s owner, which also knows whether an earlier request
 * failed and should be retried.
 */
@Composable
internal fun MarkConversationReadEffect(
    latestReplyId: String?,
    nearBottom: Boolean,
    markRead: (String) -> Unit,
) {
    // Keyed on the owner so a replacement owner's state is read fresh rather than carried over
    // from the previous one for a frame.
    val lifecycleOwner = LocalLifecycleOwner.current
    key(lifecycleOwner) {
        val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
        val started = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        LaunchedEffect(latestReplyId, nearBottom, started) {
            if (started && nearBottom && latestReplyId != null) {
                log.d("Requesting latest conversation reply be marked read")
                markRead(latestReplyId)
            }
        }
    }
}
