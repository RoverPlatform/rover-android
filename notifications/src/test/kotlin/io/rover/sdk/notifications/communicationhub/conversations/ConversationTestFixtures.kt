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

/**
 * Factory helpers for constructing Conversations entities and view-model rows in tests.
 * Mirrors [TestDataGenerator], which covers Posts/Subscriptions.
 *
 * Epoch-millis constants T0–T8 are consecutive UTC midnights starting 2024-01-01.
 */
internal object ConversationTestFixtures {

    const val T0 = 1704067200000L  // 2024-01-01T00:00:00Z
    const val T1 = 1704153600000L  // 2024-01-02T00:00:00Z
    const val T2 = 1704240000000L  // 2024-01-03T00:00:00Z
    const val T3 = 1704326400000L  // 2024-01-04T00:00:00Z
    const val T4 = 1704412800000L  // 2024-01-05T00:00:00Z
    const val T5 = 1704499200000L  // 2024-01-06T00:00:00Z
    const val T6 = 1704585600000L  // 2024-01-07T00:00:00Z
    const val T7 = 1704672000000L  // 2024-01-08T00:00:00Z
    const val T8 = 1704758400000L  // 2024-01-09T00:00:00Z

    fun conversation(
        id: String = "conversation-1",
        subject: String? = null,
        lastReplyAt: Long = T1,
        lastIncomingReplyAt: Long? = null,
        lastIncomingParticipantID: String? = null,
        lastReadAt: Long? = null,
        lastReadReplyID: String? = null,
        lastReplyPreview: String? = null,
        createdAt: Long = T0,
        participantIDs: List<String>? = null,
        updatedAt: Long = lastReplyAt,
    ) = ConversationEntity(
        id = id,
        subject = subject,
        lastReplyAt = lastReplyAt,
        lastIncomingReplyAt = lastIncomingReplyAt,
        lastIncomingParticipantID = lastIncomingParticipantID,
        lastReadAt = lastReadAt,
        lastReadReplyID = lastReadReplyID,
        lastReplyPreview = lastReplyPreview,
        createdAt = createdAt,
        participantIDs = participantIDs,
        updatedAt = updatedAt,
    )

    fun participant(
        id: String = "participant-1",
        name: String? = "Test User",
        avatarUrl: String? = null,
        bio: String? = null,
        updatedAt: Long = T0,
    ) = ParticipantEntity(
        id = id,
        name = name,
        bio = bio,
        avatarUrl = avatarUrl,
        updatedAt = updatedAt,
    )

    fun reply(
        id: String,
        conversationId: String = "conversation-1",
        participantId: String? = "participant-1",
        createdAt: Long = T0,
        externalID: String? = null,
        senderType: String = if (participantId == null) "fan" else "participant",
        content: List<ReplyContentBlock> = listOf(textBlock(id)),
        syncState: String = ReplyEntity.SYNC_STATE_CONFIRMED,
    ) = ReplyEntity(
        id = id,
        conversationID = conversationId,
        senderType = senderType,
        participantID = participantId,
        externalID = externalID,
        createdAt = createdAt,
        content = content,
        syncState = syncState,
    )

    fun textBlock(text: String) = ReplyContentBlock(
        type = ReplyContentBlock.TYPE_TEXT,
        text = text,
        url = null,
    )

    fun imageBlock(url: String) = ReplyContentBlock(
        type = ReplyContentBlock.TYPE_IMAGE,
        text = null,
        url = url,
    )

    internal fun replyRow(
        id: String,
        text: String = id,
        senderId: String? = "participant-1",
        senderName: String? = "Test User",
        senderAvatarUrl: String? = null,
        externalID: String? = null,
        isOutgoing: Boolean = false,
        sentAt: Long = T0,
        syncState: String = ReplyEntity.SYNC_STATE_CONFIRMED,
    ) = ConversationReplyRow(
        id = id,
        senderId = senderId,
        senderName = senderName,
        senderAvatarUrl = senderAvatarUrl,
        content = listOf(textBlock(text)),
        sentAt = sentAt,
        externalID = externalID,
        isOutgoing = isOutgoing,
        syncState = syncState,
    )
}
