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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface ConversationsDataSource {
    suspend fun hasConversation(conversationId: String): Boolean
    fun getConversationsFlow(): Flow<List<ConversationEntity>>
    fun getParticipantsFlow(): Flow<List<ParticipantEntity>>
    fun getRepliesFlow(): Flow<List<ReplyEntity>>
    fun getResetVersionFlow(): StateFlow<Long>
    fun getRepliesBackwardCursorFlow(conversationId: String): Flow<String?>
    suspend fun bootstrapLatestReplies(conversationId: String)
    fun startRepliesForwardPolling(conversationId: String)
    fun stopRepliesForwardPolling(conversationId: String)
    suspend fun loadOlderReplies(
        conversationId: String,
        beforeReplyId: String?,
        beforeCursor: String?,
    )
    suspend fun markConversationRead(conversationId: String, lastReadReplyId: String?)
    suspend fun sendReply(
        conversationId: String,
        message: String,
        externalId: String? = null,
    ): String
}
