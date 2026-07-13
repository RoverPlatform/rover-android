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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo

import io.rover.sdk.notifications.communicationhub.messages.formatConversationDayDivider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ConversationThreadRowsTest {
    private val originalTimeZone = TimeZone.getDefault()
    private val originalLocale = Locale.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    @Test
    fun emptyRepliesReturnNoRows() {
        assertThat(buildConversationThreadRows(emptyList()), equalTo(emptyList()))
    }

    @Test
    fun sameSenderWithinTwoMinutesStaysInOneGroup() {
        val rows = buildConversationThreadRows(
            listOf(
                reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false),
                reply(id = "reply-2", sentAt = "2026-01-01T08:01:30Z", senderId = "participant-1", isOutgoing = false),
            )
        )

        assertThat(rows.size, equalTo(2))
        assertThat(rows[0], equalTo(dayDivider("2026-01-01T08:00:00Z")))
        assertThat(
            rows[1],
            equalTo(
                messageGroup(
                    id = "reply-1",
                    senderKey = "participant-1",
                    senderName = "Morgan Lee",
                    senderAvatarUrl = null,
                    isOutgoing = false,
                    replies = listOf(
                        reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false),
                        reply(id = "reply-2", sentAt = "2026-01-01T08:01:30Z", senderId = "participant-1", isOutgoing = false),
                    ),
                )
            )
        )
    }

    @Test
    fun senderChangeStartsANewGroup() {
        val rows = buildConversationThreadRows(
            listOf(
                reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false),
                reply(id = "reply-2", sentAt = "2026-01-01T08:01:00Z", senderId = "participant-2", isOutgoing = false),
            )
        )

        assertThat(
            rows,
            equalTo(
                listOf(
                    dayDivider("2026-01-01T08:00:00Z"),
                    messageGroup(
                        id = "reply-1",
                        senderKey = "participant-1",
                        senderName = "Morgan Lee",
                        senderAvatarUrl = null,
                        isOutgoing = false,
                        replies = listOf(reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false)),
                    ),
                    messageGroup(
                        id = "reply-2",
                        senderKey = "participant-2",
                        senderName = "Taylor Kim",
                        senderAvatarUrl = null,
                        isOutgoing = false,
                        replies = listOf(reply(id = "reply-2", sentAt = "2026-01-01T08:01:00Z", senderId = "participant-2", isOutgoing = false)),
                    ),
                )
            )
        )
    }

    @Test
    fun participantIdChangeStartsANewInboundGroup() {
        val rows = buildConversationThreadRows(
            listOf(
                reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false),
                reply(id = "reply-2", sentAt = "2026-01-01T08:01:00Z", senderId = "participant-2", isOutgoing = false),
            )
        )

        assertThat(rows[1], equalTo(ConversationThreadRow.MessageGroup(
            id = "reply-1",
            senderKey = "participant-1",
            senderName = "Morgan Lee",
            senderAvatarUrl = null,
            isOutgoing = false,
            replies = listOf(reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false)),
        )))
        assertThat(rows[2], equalTo(ConversationThreadRow.MessageGroup(
            id = "reply-2",
            senderKey = "participant-2",
            senderName = "Taylor Kim",
            senderAvatarUrl = null,
            isOutgoing = false,
            replies = listOf(reply(id = "reply-2", sentAt = "2026-01-01T08:01:00Z", senderId = "participant-2", isOutgoing = false)),
        )))
    }

    @Test
    fun timeGapOverTwoMinutesStartsANewGroup() {
        val rows = buildConversationThreadRows(
            listOf(
                reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false),
                reply(id = "reply-2", sentAt = "2026-01-01T08:03:01Z", senderId = "participant-1", isOutgoing = false),
            )
        )

        assertThat(rows[1], equalTo(ConversationThreadRow.MessageGroup(
            id = "reply-1",
            senderKey = "participant-1",
            senderName = "Morgan Lee",
            senderAvatarUrl = null,
            isOutgoing = false,
            replies = listOf(reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false)),
        )))
        assertThat(rows[2], equalTo(ConversationThreadRow.MessageGroup(
            id = "reply-2",
            senderKey = "participant-1",
            senderName = "Morgan Lee",
            senderAvatarUrl = null,
            isOutgoing = false,
            replies = listOf(reply(id = "reply-2", sentAt = "2026-01-01T08:03:01Z", senderId = "participant-1", isOutgoing = false)),
        )))
    }

    @Test
    fun dayChangeAddsExactlyOneDividerPerDay() {
        val rows = buildConversationThreadRows(
            listOf(
                reply(id = "reply-1", sentAt = "2026-01-02T07:30:00Z", senderId = "participant-1", isOutgoing = false),
                reply(id = "reply-2", sentAt = "2026-01-02T08:30:00Z", senderId = "participant-1", isOutgoing = false),
            )
        )

        assertThat(rows[0], equalTo(dayDivider("2026-01-02T07:30:00Z")))
        assertThat(rows[2], equalTo(dayDivider("2026-01-02T08:30:00Z")))
        assertThat(rows[1], equalTo(messageGroup(
            id = "reply-1",
            senderKey = "participant-1",
            senderName = "Morgan Lee",
            senderAvatarUrl = null,
            isOutgoing = false,
            replies = listOf(reply(id = "reply-1", sentAt = "2026-01-02T07:30:00Z", senderId = "participant-1", isOutgoing = false)),
        )))
        assertThat(rows[3], equalTo(messageGroup(
            id = "reply-2",
            senderKey = "participant-1",
            senderName = "Morgan Lee",
            senderAvatarUrl = null,
            isOutgoing = false,
            replies = listOf(reply(id = "reply-2", sentAt = "2026-01-02T08:30:00Z", senderId = "participant-1", isOutgoing = false)),
        )))
    }

    @Test
    fun appendingRepliesDoesNotChangeExistingRowIdentity() {
        val initialRows = buildConversationThreadRows(
            listOf(
                reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false),
                reply(id = "reply-2", sentAt = "2026-01-01T08:01:00Z", senderId = "participant-1", isOutgoing = false),
            )
        )
        val appendedRows = buildConversationThreadRows(
            listOf(
                reply(id = "reply-1", sentAt = "2026-01-01T08:00:00Z", senderId = "participant-1", isOutgoing = false),
                reply(id = "reply-2", sentAt = "2026-01-01T08:01:00Z", senderId = "participant-1", isOutgoing = false),
                reply(id = "reply-3", sentAt = "2026-01-01T08:02:30Z", senderId = "participant-1", isOutgoing = false),
            )
        )

        assertThat(initialRows.map { identityOf(it) }, equalTo(appendedRows.take(initialRows.size).map { identityOf(it) }))
        assertThat(
            (initialRows[1] as ConversationThreadRow.MessageGroup).id,
            equalTo((appendedRows[1] as ConversationThreadRow.MessageGroup).id)
        )
    }

    @Test
    fun hasQueuedReplyIsTrueWhenAnOutgoingReplyIsQueued() {
        val replies = listOf(
            ConversationTestFixtures.replyRow(id = "r1", isOutgoing = true, syncState = ReplyEntity.SYNC_STATE_SENT),
            ConversationTestFixtures.replyRow(id = "r2", isOutgoing = true, syncState = ReplyEntity.SYNC_STATE_QUEUED),
        )
        assertThat(replies.hasQueuedReply(), equalTo(true))
    }

    @Test
    fun hasQueuedReplyIsFalseWhenNoOutgoingReplyIsQueued() {
        val replies = listOf(
            ConversationTestFixtures.replyRow(id = "r1", isOutgoing = true, syncState = ReplyEntity.SYNC_STATE_SENT),
            ConversationTestFixtures.replyRow(id = "r2", isOutgoing = false, syncState = ReplyEntity.SYNC_STATE_CONFIRMED),
        )
        assertThat(replies.hasQueuedReply(), equalTo(false))
    }

    @Test
    fun failedOutgoingReplyAlwaysResolvesToFailedLabel() {
        // Even when it is not the last reply of the most recent group.
        val reply = ConversationTestFixtures.replyRow(id = "r1", isOutgoing = true, syncState = ReplyEntity.SYNC_STATE_FAILED)
        val label = replyStatusLabelFor(
            reply = reply,
            isLastInGroup = false,
            isMostRecentGroup = false,
            groupHasQueuedReply = false,
        )
        assertEquals(ReplyStatusLabel.Failed, label)
    }

    @Test
    fun failedTakesPriorityOverTimestampOnLastReplyOfMostRecentGroup() {
        val reply = ConversationTestFixtures.replyRow(id = "r1", isOutgoing = true, syncState = ReplyEntity.SYNC_STATE_FAILED)
        val label = replyStatusLabelFor(
            reply = reply,
            isLastInGroup = true,
            isMostRecentGroup = true,
            groupHasQueuedReply = false,
        )
        assertEquals(ReplyStatusLabel.Failed, label)
    }

    @Test
    fun lastReplyOfMostRecentGroupWithQueuedReplyResolvesToSending() {
        val reply = ConversationTestFixtures.replyRow(id = "r1", isOutgoing = true, syncState = ReplyEntity.SYNC_STATE_QUEUED)
        val label = replyStatusLabelFor(
            reply = reply,
            isLastInGroup = true,
            isMostRecentGroup = true,
            groupHasQueuedReply = true,
        )
        assertEquals(ReplyStatusLabel.Sending, label)
    }

    @Test
    fun lastReplyOfMostRecentGroupWithoutQueuedReplyResolvesToTimestamp() {
        val reply = ConversationTestFixtures.replyRow(
            id = "r1",
            isOutgoing = true,
            sentAt = 1_704_067_200_000L,
            syncState = ReplyEntity.SYNC_STATE_CONFIRMED,
        )
        val label = replyStatusLabelFor(
            reply = reply,
            isLastInGroup = true,
            isMostRecentGroup = true,
            groupHasQueuedReply = false,
        )
        assertEquals(ReplyStatusLabel.Timestamp(1_704_067_200_000L), label)
    }

    @Test
    fun nonLastReplyShowsNoLabelWhenNotFailed() {
        val reply = ConversationTestFixtures.replyRow(id = "r1", isOutgoing = true, syncState = ReplyEntity.SYNC_STATE_CONFIRMED)
        val label = replyStatusLabelFor(
            reply = reply,
            isLastInGroup = false,
            isMostRecentGroup = true,
            groupHasQueuedReply = false,
        )
        assertNull(label)
    }

    @Test
    fun lastReplyOfNonMostRecentGroupShowsNoLabel() {
        val reply = ConversationTestFixtures.replyRow(id = "r1", isOutgoing = true, syncState = ReplyEntity.SYNC_STATE_CONFIRMED)
        val label = replyStatusLabelFor(
            reply = reply,
            isLastInGroup = true,
            isMostRecentGroup = false,
            groupHasQueuedReply = false,
        )
        assertNull(label)
    }

    private fun reply(
        id: String,
        sentAt: String,
        senderId: String?,
        isOutgoing: Boolean,
    ): ConversationReplyRow = ConversationTestFixtures.replyRow(
        id = id,
        senderId = senderId,
        senderName = if (senderId == null) null else if (senderId == "participant-1") "Morgan Lee" else "Taylor Kim",
        isOutgoing = isOutgoing,
        sentAt = java.time.Instant.parse(sentAt).toEpochMilli(),
    )

    private fun dayDivider(iso: String): ConversationThreadRow.DayDivider {
        val dayStart = dayStartMillis(iso)
        return ConversationThreadRow.DayDivider(
            dateLabel = formatConversationDayDivider(Date(dayStart)),
            dayStartMillis = dayStart,
        )
    }

    private fun messageGroup(
        id: String,
        senderKey: String?,
        senderName: String?,
        senderAvatarUrl: String?,
        isOutgoing: Boolean,
        replies: List<ConversationReplyRow>,
    ): ConversationThreadRow.MessageGroup = ConversationThreadRow.MessageGroup(
        id = id,
        senderKey = senderKey,
        senderName = senderName,
        senderAvatarUrl = senderAvatarUrl,
        isOutgoing = isOutgoing,
        replies = replies,
    )

    private fun dayStartMillis(iso: String): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = java.time.Instant.parse(iso).toEpochMilli()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun identityOf(row: ConversationThreadRow): String = when (row) {
        is ConversationThreadRow.DayDivider -> "day:${row.dayStartMillis}"
        is ConversationThreadRow.MessageGroup -> "group:${row.id}"
    }
}
