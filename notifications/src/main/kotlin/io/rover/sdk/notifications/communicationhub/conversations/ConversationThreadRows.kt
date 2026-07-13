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

import io.rover.sdk.notifications.communicationhub.messages.formatConversationDayDivider
import java.util.Calendar
import java.util.Date

internal sealed interface ConversationThreadRow {
    data class DayDivider(
        val dateLabel: String,
        val dayStartMillis: Long,
    ) : ConversationThreadRow

    data class MessageGroup(
        val id: String,
        val senderKey: String?,
        val senderName: String?,
        val senderAvatarUrl: String?,
        val isOutgoing: Boolean,
        val replies: List<ConversationReplyRow>,
    ) : ConversationThreadRow
}

/**
 * The status indicator shown beneath a reply bubble. Mirrors the iOS precedence in
 * `MessageGroupBubbleView`: a failed outgoing reply always shows "Not Delivered"; otherwise the
 * last bubble of the most recent group shows either "Sending…" (when the group has a queued reply)
 * or the group's timestamp. Every other reply shows nothing.
 */
internal sealed interface ReplyStatusLabel {
    data object Failed : ReplyStatusLabel
    data object Sending : ReplyStatusLabel
    data class Timestamp(val sentAtMillis: Long) : ReplyStatusLabel
}

/**
 * Resolves the status label for a single reply within its group, matching iOS exactly:
 *
 * - A failed outgoing reply shows [ReplyStatusLabel.Failed] regardless of position.
 * - Otherwise, only the last reply of the most recent group shows a label: [ReplyStatusLabel.Sending]
 *   when any reply in the group is a queued outgoing reply, else [ReplyStatusLabel.Timestamp].
 * - All other replies show no label.
 */
internal fun replyStatusLabelFor(
    reply: ConversationReplyRow,
    isLastInGroup: Boolean,
    isMostRecentGroup: Boolean,
    groupHasQueuedReply: Boolean,
): ReplyStatusLabel? = when {
    reply.isOutgoing && reply.syncState == ReplyEntity.SYNC_STATE_FAILED -> ReplyStatusLabel.Failed
    isLastInGroup && isMostRecentGroup ->
        if (groupHasQueuedReply) ReplyStatusLabel.Sending else ReplyStatusLabel.Timestamp(reply.sentAt)
    else -> null
}

/** True when any reply in the group is a queued outgoing reply (i.e. still being sent). */
internal fun List<ConversationReplyRow>.hasQueuedReply(): Boolean =
    any { it.isOutgoing && it.syncState == ReplyEntity.SYNC_STATE_QUEUED }

internal fun buildConversationThreadRows(
    replies: List<ConversationReplyRow>,
): List<ConversationThreadRow> {
    if (replies.isEmpty()) return emptyList()

    val parsedReplies = replies.map { reply ->
        ParsedReply(
            reply = reply,
            sentAtMillis = reply.sentAt,
            dayStartMillis = startOfDayMillis(reply.sentAt),
        )
    }

    val rows = mutableListOf<ConversationThreadRow>()
    var currentGroup = mutableListOf(parsedReplies.first().reply)

    rows += ConversationThreadRow.DayDivider(
        dateLabel = formatDayLabel(parsedReplies.first().dayStartMillis),
        dayStartMillis = parsedReplies.first().dayStartMillis,
    )

    for (index in 1 until parsedReplies.size) {
        val previous = parsedReplies[index - 1]
        val current = parsedReplies[index]
        val startsNewDay = previous.dayStartMillis != current.dayStartMillis
        val startsNewGroup = startsNewDay || shouldStartNewGroup(previous.reply, current.reply, previous.sentAtMillis, current.sentAtMillis)

        if (startsNewDay) {
            rows += ConversationThreadRow.MessageGroup(
                id = currentGroup.first().id,
                senderKey = currentGroup.first().senderId,
                senderName = currentGroup.first().senderName,
                senderAvatarUrl = currentGroup.first().senderAvatarUrl,
                isOutgoing = currentGroup.first().isOutgoing,
                replies = currentGroup.toList(),
            )
            rows += ConversationThreadRow.DayDivider(
                dateLabel = formatDayLabel(current.dayStartMillis),
                dayStartMillis = current.dayStartMillis,
            )
            currentGroup = mutableListOf(current.reply)
        } else if (startsNewGroup) {
            rows += ConversationThreadRow.MessageGroup(
                id = currentGroup.first().id,
                senderKey = currentGroup.first().senderId,
                senderName = currentGroup.first().senderName,
                senderAvatarUrl = currentGroup.first().senderAvatarUrl,
                isOutgoing = currentGroup.first().isOutgoing,
                replies = currentGroup.toList(),
            )
            currentGroup = mutableListOf(current.reply)
        } else {
            currentGroup += current.reply
        }
    }

    rows += ConversationThreadRow.MessageGroup(
        id = currentGroup.first().id,
        senderKey = currentGroup.first().senderId,
        senderName = currentGroup.first().senderName,
        senderAvatarUrl = currentGroup.first().senderAvatarUrl,
        isOutgoing = currentGroup.first().isOutgoing,
        replies = currentGroup,
    )

    return rows
}

private fun shouldStartNewGroup(
    previous: ConversationReplyRow,
    current: ConversationReplyRow,
    previousSentAtMillis: Long,
    currentSentAtMillis: Long,
): Boolean {
    if (previous.isOutgoing != current.isOutgoing) return true
    if (!current.isOutgoing && previous.senderId != current.senderId) return true
    if (currentSentAtMillis - previousSentAtMillis > TWO_MINUTES_MILLIS) return true
    return false
}

private fun startOfDayMillis(epochMillis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun formatDayLabel(dayStartMillis: Long): String {
    return formatConversationDayDivider(Date(dayStartMillis))
}

private data class ParsedReply(
    val reply: ConversationReplyRow,
    val sentAtMillis: Long,
    val dayStartMillis: Long,
)

private const val TWO_MINUTES_MILLIS = 2 * 60 * 1000L
