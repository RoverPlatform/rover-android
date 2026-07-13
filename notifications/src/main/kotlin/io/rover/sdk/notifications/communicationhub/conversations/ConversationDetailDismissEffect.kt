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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * Bridges the ViewModel's one-shot [ConversationDetailEffect] flow into the Compose lifecycle.
 * Collected inside a [LaunchedEffect] so the coroutine is scoped to the composition and cancelled
 * automatically when the composable leaves the tree. Calls [onDismiss] (typically `finish()`) when
 * the ViewModel emits [ConversationDetailEffect.Dismiss] — e.g. after an unrecoverable error or
 * conversation deletion.
 */
@Composable
internal fun ConversationDetailDismissEffect(
    effects: Flow<ConversationDetailEffect>,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            if (effect is ConversationDetailEffect.Dismiss) {
                onDismiss()
            }
        }
    }
}
