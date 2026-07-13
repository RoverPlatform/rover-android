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
import io.rover.sdk.core.data.sync.SyncResetRequiredException
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import io.rover.sdk.notifications.communicationhub.conversations.dto.ConversationItem
import io.rover.sdk.notifications.communicationhub.conversations.dto.ParticipantItem
import io.rover.sdk.notifications.communicationhub.conversations.dto.ReplyContentBlockItem
import io.rover.sdk.notifications.communicationhub.conversations.dto.ReplyItem
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.data.network.MarkConversationReadRequest
import io.rover.sdk.notifications.communicationhub.data.network.SendConversationReplyRequest
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.sync.HubSyncCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsRepositoryFakeSyncTest : RoverEngageTestBase() {
    private fun buildConversationsRepository(
        dispatcher: CoroutineDispatcher? = null,
        now: (() -> Long)? = null,
    ): ConversationsRepository {
        return ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
            backgroundDispatcher = dispatcher ?: kotlinx.coroutines.Dispatchers.IO,
            now = now ?: { System.currentTimeMillis() },
        )
    }

    private suspend fun seedQueuedReply() {
        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )
        database!!.repliesDao().upsertReply(
            ReplyEntity(
                id = "local-1",
                conversationID = "conversation-1",
                senderType = ReplyEntity.SENDER_TYPE_FAN,
                participantID = null,
                externalID = "external-1",
                createdAt = System.currentTimeMillis(),
                content = listOf(ReplyContentBlock(ReplyContentBlock.TYPE_TEXT, "Hello", null)),
                syncState = ReplyEntity.SYNC_STATE_QUEUED,
                retryCount = 0,
                nextRetryAt = null,
                lastSendError = null,
            )
        )
    }

    private suspend fun seedConversation(id: String) {
        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = id,
                subject = "",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )
    }

    /**
     * Seeds a queued outbound reply. IMPORTANT: [createdAt] anchors the 2-minute retry window — a
     * flush's deadline sweep terminally fails any queued reply older than `now - 120s` *before*
     * attempting it. Tests that expect a reply to actually send must use a recent [createdAt] (e.g.
     * `System.currentTimeMillis()`); a fixed historical timestamp will be silently swept to `failed`.
     */
    private suspend fun seedReply(
        id: String,
        conversationId: String,
        externalId: String,
        createdAt: Long,
        retryCount: Int = 0,
        nextRetryAt: Long? = null,
    ) {
        database!!.repliesDao().upsertReply(
            ReplyEntity(
                id = id,
                conversationID = conversationId,
                senderType = ReplyEntity.SENDER_TYPE_FAN,
                participantID = null,
                externalID = externalId,
                createdAt = createdAt,
                content = listOf(ReplyContentBlock(ReplyContentBlock.TYPE_TEXT, "Hello", null)),
                syncState = ReplyEntity.SYNC_STATE_QUEUED,
                retryCount = retryCount,
                nextRetryAt = nextRetryAt,
                lastSendError = null,
            )
        )
    }

    private fun buildConversationsSync(
        conversationsRepository: ConversationsRepository,
        dispatcher: CoroutineDispatcher? = null,
    ): ConversationsSync {
        return ConversationsSync(
            conversationsRepository = conversationsRepository,
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            backgroundDispatcher = dispatcher ?: kotlinx.coroutines.Dispatchers.IO,
        )
    }

    private fun buildFullHubSyncCoordinator(
        engageApiService: EngageApiService,
        hubCoordinator: HubCoordinator = HubCoordinator(),
    ): HubSyncCoordinator {
        val database = database!!
        return HubSyncCoordinator(
            engageApiService = engageApiService,
            postsDao = database.postsDao(),
            subscriptionsDao = database.subscriptionsDao(),
            conversationsDao = database.conversationsDao(),
            repliesDao = database.repliesDao(),
            participantsDao = database.participantsDao(),
            syncStateDao = database.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database),
            hubCoordinator = hubCoordinator,
        )
    }

    private open class UnsupportedEngageApiService : EngageApiService {
        override suspend fun getPosts(deviceIdentifier: String, cursor: String?): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun getSubscriptions(): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun getParticipants(): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun sendConversationReply(
            conversationId: String,
            body: SendConversationReplyRequest,
        ): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun getConversations(
            include: String?,
            cursor: String?,
            before: String?,
        ): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun getConversationReplies(
            conversationId: String,
            cursor: String?,
            before: String?,
        ): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun markConversationRead(
            conversationId: String,
            body: MarkConversationReadRequest,
        ): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()
    }

    @Test
    fun fakeSyncPersistsConversationReplyParticipantAndSyncState() = runBlocking {
        val conversationsRepository = buildConversationsRepository()

        conversationsRepository.fakeSyncConversations(
            conversations = listOf(
                ConversationItem(
                    id = "conversation-1",
                    subject = null,
                    lastReplyAt = "2024-01-04T00:00:00Z",
                    lastIncomingReplyAt = "2024-01-03T00:00:00Z",
                    lastIncomingParticipantID = null,
                    lastReadAt = "2024-01-02T00:00:00Z",
                    lastReadReplyID = "reply-1",
                    lastReplyPreview = "Hello again",
                    createdAt = "2024-01-01T00:00:00Z",
                    participantIDs = null,
                    updatedAt = "2024-01-05T00:00:00Z",
                )
            ),
            participants = listOf(
                ParticipantItem(
                    id = "participant-1",
                    name = "Casey Jones",
                    avatarURL = null,
                    updatedAt = "2024-01-01T00:00:00Z",
                )
            ),
            forwardCursor = "next-conversations",
            backwardCursor = "older-conversations",
            historyComplete = true,
        )

        conversationsRepository.fakeSyncReplies(
            conversationId = "conversation-1",
            replies = listOf(
                ReplyItem(
                    id = "reply-1",
                    conversationID = "conversation-1",
                    senderType = "participant",
                    participantID = "participant-1",
                    content = listOf(
                        ReplyContentBlockItem.text("Hello world")
                    ),
                    externalID = "external-1",
                    createdAt = "2024-01-06T00:00:00Z",
                )
            ),
            forwardCursor = "next-replies",
            backwardCursor = "older-replies",
        )

        val conversation = database!!.conversationsDao().getConversationById("conversation-1")
        assertThat(conversation?.participantIDs, equalTo(null as List<String>?))
        assertThat(conversation?.subject, equalTo(null as String?))
        assertThat(conversation?.lastReplyAt, equalTo(1704326400000L))
        assertThat(conversation?.lastIncomingReplyAt, equalTo(1704240000000L))
        assertThat(conversation?.lastReadAt, equalTo(1704153600000L))
        assertThat(conversation?.lastReadReplyID, equalTo("reply-1"))
        assertThat(conversation?.lastReplyPreview, equalTo("Hello again"))
        assertThat(conversation?.createdAt, equalTo(1704067200000L))
        assertThat(conversation?.updatedAt, equalTo(1704412800000L))

        val participant = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(participant?.name, equalTo("Casey Jones"))
        assertThat(participant?.avatarUrl, equalTo(null))

        val replies = database!!.repliesDao().getRepliesForConversation("conversation-1")
        assertThat(replies.size, equalTo(1))
        assertThat(replies.first().senderType, equalTo("participant"))
        assertThat(replies.first().externalID, equalTo("external-1"))
        assertThat(replies.first().createdAt, equalTo(1704499200000L))

        val conversationsState = database!!.syncStateDao().getSyncState("conversations")
        assertThat(conversationsState?.forwardCursor, equalTo("next-conversations"))
        assertThat(conversationsState?.backwardCursor, equalTo("older-conversations"))
        assertThat(conversationsState?.historyComplete, equalTo(true))

        val repliesState = database!!.syncStateDao().getSyncState("replies:conversation-1")
        assertThat(repliesState?.forwardCursor, equalTo("next-replies"))
        assertThat(repliesState?.backwardCursor, equalTo("older-replies"))
    }

    @Test
    fun fakeSyncConversationsMergesParticipantsWithoutDegradingExistingRows() = runBlocking {
        val conversationsRepository = buildConversationsRepository()

        database!!.participantsDao().upsertParticipant(
            ParticipantItem(
                id = "participant-1",
                name = "Casey Jones",
                avatarURL = "https://example.com/casey.png",
                updatedAt = "2024-01-01T00:00:00Z",
            ).toEntity()
        )

        // A degraded participant row in a sync page (null name/avatar) must not clobber the
        // good values already stored locally.
        conversationsRepository.fakeSyncConversations(
            conversations = emptyList(),
            participants = listOf(
                ParticipantItem(
                    id = "participant-1",
                    name = null,
                    avatarURL = null,
                    updatedAt = "2024-01-02T00:00:00Z",
                )
            ),
            forwardCursor = null,
            backwardCursor = null,
            historyComplete = false,
        )

        val participant = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(participant?.name, equalTo("Casey Jones"))
        assertThat(participant?.avatarUrl, equalTo("https://example.com/casey.png"))
    }

    @Test
    fun fakeSyncRepliesRejectsConversationIdMismatch() {
        val conversationsRepository = buildConversationsRepository()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                conversationsRepository.fakeSyncReplies(
                    conversationId = "conversation-1",
                    replies = listOf(
                        ReplyItem(
                            id = "reply-1",
                            conversationID = "conversation-2",
                            senderType = "participant",
                            participantID = null,
                            content = listOf(
                                ReplyContentBlockItem.text("Hello world")
                            ),
                            externalID = null,
                            createdAt = "2024-01-06T00:00:00Z",
                        )
                    ),
                    forwardCursor = null,
                    backwardCursor = null,
                )
            }
        }
    }

    @Test
    fun fakeSyncConversationsKeepsHistoryCompleteStickyOnceTrue() = runBlocking {
        val conversationsRepository = buildConversationsRepository()

        conversationsRepository.fakeSyncConversations(
            conversations = emptyList(),
            participants = emptyList(),
            forwardCursor = "next-1",
            backwardCursor = "older-1",
            historyComplete = true,
        )

        conversationsRepository.fakeSyncConversations(
            conversations = emptyList(),
            participants = emptyList(),
            forwardCursor = "next-2",
            backwardCursor = "older-2",
            historyComplete = false,
        )

        val conversationsState = database!!.syncStateDao().getSyncState("conversations")
        assertThat(conversationsState?.historyComplete, equalTo(true))
        assertThat(conversationsState?.forwardCursor, equalTo("next-2"))
        assertThat(conversationsState?.backwardCursor, equalTo("older-2"))
    }

    @Test
    fun conversationsSync410ClearsOnlyConversationData() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        val conversationsSync = buildConversationsSync(conversationsRepository)

        seedConversationData()
        seedPostsAndSubscriptions()

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversations(any(), anyOrNull(), anyOrNull())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                conversationsSync.triggerEagerBackwardHistoryBackfill()
            }
        }

        assertConversationDataCleared()
        assertPostsAndSubscriptionsUntouched()
    }

    @Test
    fun conversationsResetSignalAdvancesWhen410Occurs() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        val conversationsSync = buildConversationsSync(conversationsRepository)

        val resetEvent = conversationsRepository.getResetVersionFlow()

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversations(include = "participants", cursor = null, before = null)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                conversationsSync.triggerEagerBackwardHistoryBackfill()
            }
        }

        assertThat(resetEvent.first { it > 0 }, equalTo(1L))
    }

    @Test
    fun repliesSync410ClearsOnlyConversationData() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        seedConversationData()
        seedPostsAndSubscriptions()

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversationReplies(any(), anyOrNull(), anyOrNull())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                conversationsRepository.bootstrapLatestReplies("conversation-1")
            }
        }

        assertConversationDataCleared()
        assertPostsAndSubscriptionsUntouched()
    }

    @Test
    fun sendReply410ClearsOnlyConversationData() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        seedConversationData()
        seedPostsAndSubscriptions()

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .sendConversationReply(any(), any())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                conversationsRepository.sendReply(
                    conversationId = "conversation-1",
                    message = "Hello",
                )
            }
        }

        assertConversationDataCleared()
        assertPostsAndSubscriptionsUntouched()
    }

    @Test
    fun markRead410ClearsOnlyConversationData() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        seedConversationData()
        seedPostsAndSubscriptions()

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .markConversationRead(any(), any())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                conversationsRepository.markConversationRead(
                    conversationId = "conversation-1",
                    lastReadReplyId = "reply-1",
                )
            }
        }

        assertConversationDataCleared()
        assertPostsAndSubscriptionsUntouched()
    }

    @Test
    fun existingThreadSendCreatesPendingReplyAndTreats202AsAccepted() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )

        doReturn(Response.success<Void>(202, null))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-1"), any())

        val externalId = conversationsRepository.sendReply(
            conversationId = "conversation-1",
            message = "Hello"
        )

        val requestCaptor = argumentCaptor<io.rover.sdk.notifications.communicationhub.data.network.SendConversationReplyRequest>()
        verify(mockEngageApiService).sendConversationReply(eq("conversation-1"), any())
        verify(mockEngageApiService).sendConversationReply(eq("conversation-1"), requestCaptor.capture())

        assertThat(requestCaptor.firstValue.content, equalTo(listOf(ReplyContentBlockItem.text("Hello"))))
        assertThat(requestCaptor.firstValue.externalID, equalTo(externalId))

        val replies = database!!.repliesDao().getRepliesForConversation("conversation-1")
        assertThat(replies.size, equalTo(1))
        assertThat(replies.first().id, equalTo(externalId))
        assertThat(replies.first().conversationID, equalTo("conversation-1"))
        assertThat(replies.first().senderType, equalTo("fan"))
        assertThat(replies.first().participantID, equalTo(null as String?))
        assertThat(replies.first().externalID, equalTo(externalId))
        assertThat(replies.first().content, equalTo(listOf(io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock(
            type = io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock.TYPE_TEXT,
            text = "Hello",
            url = null,
        ))))
    }

    @Test
    fun existingThreadRetryableSendFailureLeavesQueuedReply() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )

        doReturn(Response.error<okhttp3.ResponseBody>(500, "nope".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-1"), any())

        // A retryable failure leaves the reply queued for a later retry. The reply is still
        // legitimately pending, so sendReply does NOT surface it to the caller as an error.
        val returnedId = conversationsRepository.sendReply(
            conversationId = "conversation-1",
            message = "Hello",
            externalId = "external-1",
        )
        assertThat(returnedId, equalTo("external-1"))

        val replies = database!!.repliesDao().getRepliesForConversation("conversation-1")
        assertThat(replies.size, equalTo(1))
        assertThat(replies.first().id, equalTo("external-1"))
        assertThat(replies.first().externalID, equalTo("external-1"))
        assertThat(replies.first().syncState, equalTo(ReplyEntity.SYNC_STATE_QUEUED))
        assertThat(replies.first().retryCount, equalTo(1))
        assertThat(replies.first().nextRetryAt != null, equalTo(true))
        assertThat(replies.first().lastSendError, equalTo("Failed to send reply. HTTP 500"))
    }

    @Test
    fun existingThreadTerminalSendFailureLeavesFailedReply() = runBlocking {
        val conversationsRepository = buildConversationsRepository()

        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )

        doReturn(Response.error<okhttp3.ResponseBody>(400, "bad".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-1"), any())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                conversationsRepository.sendReply(
                    conversationId = "conversation-1",
                    message = "Hello",
                    externalId = "external-1",
                )
            }
        }

        val reply = database!!.repliesDao().getRepliesForConversation("conversation-1").first()
        assertThat(reply.syncState, equalTo(ReplyEntity.SYNC_STATE_FAILED))
        assertThat(reply.retryCount, equalTo(1))
        assertThat(reply.nextRetryAt, equalTo(null as Long?))
        assertThat(reply.lastSendError, equalTo("Failed to send reply. HTTP 400"))
    }

    @Test
    fun flushQueuedRepliesSendsDueReplyAndMarksSent() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )
        database!!.repliesDao().upsertReply(
            ReplyEntity(
                id = "local-1",
                conversationID = "conversation-1",
                senderType = ReplyEntity.SENDER_TYPE_FAN,
                participantID = null,
                externalID = "external-1",
                createdAt = System.currentTimeMillis(),
                content = listOf(ReplyContentBlock(ReplyContentBlock.TYPE_TEXT, "Hello", null)),
                syncState = ReplyEntity.SYNC_STATE_QUEUED,
                retryCount = 2,
                nextRetryAt = 0L,
                lastSendError = "network",
            )
        )
        doReturn(Response.success<Void>(202, null))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-1"), any())

        val allSucceeded = conversationsRepository.requestFlush()

        val requestCaptor = argumentCaptor<SendConversationReplyRequest>()
        verify(mockEngageApiService).sendConversationReply(eq("conversation-1"), requestCaptor.capture())
        assertThat(allSucceeded, equalTo(true))
        assertThat(requestCaptor.firstValue.externalID, equalTo("external-1"))
        assertThat(requestCaptor.firstValue.content, equalTo(listOf(ReplyContentBlockItem.text("Hello"))))
        val reply = database!!.repliesDao().getRepliesForConversation("conversation-1").first()
        assertThat(reply.syncState, equalTo(ReplyEntity.SYNC_STATE_SENT))
        assertThat(reply.retryCount, equalTo(0))
        assertThat(reply.nextRetryAt, equalTo(null as Long?))
        assertThat(reply.lastSendError, equalTo(null as String?))
    }

    @Test
    fun retryableFailurePastTwoMinuteDeadlineMarksReplyFailed() = runBlocking {
        val now = 1_000_000L
        val conversationsRepository = buildConversationsRepository(now = { now })
        seedConversation("conversation-1")
        // Keep createdAt inside the 120s deadline-sweep window (so the reply survives the sweep and
        // a send is actually attempted), but old enough that the next backoff after this retryable
        // failure would land past createdAt + 120s — exercising markReplySendFailure's deadline
        // check rather than the upfront sweep. nextRetryAt = 0 keeps it eligible for this flush.
        val createdAt = now - 100_000L
        seedReply(
            id = "local-1",
            conversationId = "conversation-1",
            externalId = "external-1",
            createdAt = createdAt,
            retryCount = 5,
            nextRetryAt = 0L,
        )
        doReturn(Response.error<okhttp3.ResponseBody>(500, "nope".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-1"), any())

        conversationsRepository.requestFlush()

        verify(mockEngageApiService).sendConversationReply(eq("conversation-1"), any())
        val reply = database!!.repliesDao().getReplyByExternalId("external-1")
        assertThat(reply?.syncState, equalTo(ReplyEntity.SYNC_STATE_FAILED))
        assertThat(reply?.nextRetryAt, equalTo(null as Long?))
    }

    @Test
    fun retryDelayNeverExceedsThirtySeconds() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        seedConversation("conversation-1")
        // Fresh createdAt (well within the 2-minute window) so the reply re-queues rather than
        // failing; a high retryCount drives the exponential backoff into the capped region.
        val now = java.time.Instant.now().toEpochMilli()
        seedReply(
            id = "local-1",
            conversationId = "conversation-1",
            externalId = "external-1",
            createdAt = now,
            retryCount = 5,
            nextRetryAt = 0L,
        )
        doReturn(Response.error<okhttp3.ResponseBody>(500, "nope".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-1"), any())

        val before = java.time.Instant.now().toEpochMilli()
        conversationsRepository.requestFlush()

        val reply = database!!.repliesDao().getReplyByExternalId("external-1")
        assertThat(reply?.syncState, equalTo(ReplyEntity.SYNC_STATE_QUEUED))
        // The scheduled delay is capped at 30s — far below the uncapped 2^6 = 64s this retry
        // count would otherwise produce.
        val delay = reply!!.nextRetryAt!! - before
        assertThat(delay in 29_000L..32_000L, equalTo(true))
    }

    @Test
    fun olderReplyInBackoffBlocksNewerReplyInSameConversation() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        seedConversation("conversation-1")
        val now = java.time.Instant.now().toEpochMilli()
        // Older reply is mid-backoff (nextRetryAt in the future) so it is not yet eligible.
        seedReply(
            id = "local-old",
            conversationId = "conversation-1",
            externalId = "external-old",
            createdAt = now - 10_000L,
            retryCount = 1,
            nextRetryAt = now + 60_000L,
        )
        // Newer reply would be immediately eligible on its own.
        seedReply(
            id = "local-new",
            conversationId = "conversation-1",
            externalId = "external-new",
            createdAt = now,
            retryCount = 0,
            nextRetryAt = null,
        )

        conversationsRepository.requestFlush()

        // Head-of-line blocking: neither reply is sent because the older one is still in backoff.
        verify(mockEngageApiService, never()).sendConversationReply(any(), any())
        assertThat(
            database!!.repliesDao().getReplyByExternalId("external-old")?.syncState,
            equalTo(ReplyEntity.SYNC_STATE_QUEUED),
        )
        assertThat(
            database!!.repliesDao().getReplyByExternalId("external-new")?.syncState,
            equalTo(ReplyEntity.SYNC_STATE_QUEUED),
        )
    }

    @Test
    fun backoffInOneConversationDoesNotBlockAnother() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        seedConversation("conversation-blocked")
        seedConversation("conversation-ready")
        val now = java.time.Instant.now().toEpochMilli()
        // Conversation A: stuck in backoff.
        seedReply(
            id = "local-blocked",
            conversationId = "conversation-blocked",
            externalId = "external-blocked",
            createdAt = now,
            retryCount = 1,
            nextRetryAt = now + 60_000L,
        )
        // Conversation B: eligible and should send independently.
        seedReply(
            id = "local-ready",
            conversationId = "conversation-ready",
            externalId = "external-ready",
            createdAt = now,
            retryCount = 0,
            nextRetryAt = null,
        )
        doReturn(Response.success<Void>(202, null))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-ready"), any())

        conversationsRepository.requestFlush()

        verify(mockEngageApiService).sendConversationReply(eq("conversation-ready"), any())
        verify(mockEngageApiService, never()).sendConversationReply(eq("conversation-blocked"), any())
        assertThat(
            database!!.repliesDao().getReplyByExternalId("external-ready")?.syncState,
            equalTo(ReplyEntity.SYNC_STATE_SENT),
        )
        assertThat(
            database!!.repliesDao().getReplyByExternalId("external-blocked")?.syncState,
            equalTo(ReplyEntity.SYNC_STATE_QUEUED),
        )
    }

    @Test
    fun largeBlockedConversationDoesNotStarveAnotherConversation() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        seedConversation("conversation-blocked")
        seedConversation("conversation-ready")
        val now = java.time.Instant.now().toEpochMilli()
        // Conversation A has a full page (50) of queued replies, all older than conversation B's, and
        // its head is stuck in backoff. With a single global LIMIT 50 page these 50 rows would fill
        // the page and B would never be seen. Paging per-conversation keeps the two independent.
        seedReply(
            id = "blocked-head",
            conversationId = "conversation-blocked",
            externalId = "external-blocked-head",
            createdAt = now,
            retryCount = 1,
            nextRetryAt = now + 60_000L,
        )
        for (i in 1 until 50) {
            seedReply(
                id = "blocked-$i",
                conversationId = "conversation-blocked",
                externalId = "external-blocked-$i",
                createdAt = now + i,
                retryCount = 0,
                nextRetryAt = null,
            )
        }
        // Conversation B's reply is newer than every conversation-A reply, so it sorts last globally.
        seedReply(
            id = "local-ready",
            conversationId = "conversation-ready",
            externalId = "external-ready",
            createdAt = now + 1_000L,
            retryCount = 0,
            nextRetryAt = null,
        )
        doReturn(Response.success<Void>(202, null))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-ready"), any())

        conversationsRepository.requestFlush()

        // B sends despite A owning a full page; A sends nothing because its head is in backoff.
        verify(mockEngageApiService).sendConversationReply(eq("conversation-ready"), any())
        verify(mockEngageApiService, never()).sendConversationReply(eq("conversation-blocked"), any())
        assertThat(
            database!!.repliesDao().getReplyByExternalId("external-ready")?.syncState,
            equalTo(ReplyEntity.SYNC_STATE_SENT),
        )
    }

    @Test
    fun freshReplyQueuedBehindOlderPendingReplyIsNotSurfacedAsError() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        seedConversation("conversation-1")
        val now = java.time.Instant.now().toEpochMilli()
        // An older reply in the same conversation is mid-backoff and blocks the head of line.
        seedReply(
            id = "local-old",
            conversationId = "conversation-1",
            externalId = "external-old",
            createdAt = now - 10_000L,
            retryCount = 1,
            nextRetryAt = now + 60_000L,
        )

        // The new reply legitimately waits behind the older one; sendReply must not throw.
        val returnedId = conversationsRepository.sendReply(
            conversationId = "conversation-1",
            message = "New message",
            externalId = "external-new",
        )

        assertThat(returnedId, equalTo("external-new"))
        verify(mockEngageApiService, never()).sendConversationReply(any(), any())
        assertThat(
            database!!.repliesDao().getReplyByExternalId("external-new")?.syncState,
            equalTo(ReplyEntity.SYNC_STATE_QUEUED),
        )
    }

    @Test
    fun expiredQueuedReplyIsSweptToFailedWithoutSendAttempt() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        seedConversation("conversation-1")
        // createdAt is well past the 2-minute window and the reply is otherwise due (nextRetryAt = 0).
        // The deadline sweep must fail it before any send is attempted.
        seedReply(
            id = "local-1",
            conversationId = "conversation-1",
            externalId = "external-1",
            createdAt = System.currentTimeMillis() - 200_000L,
            retryCount = 2,
            nextRetryAt = 0L,
        )

        conversationsRepository.requestFlush()

        verify(mockEngageApiService, never()).sendConversationReply(any(), any())
        val reply = database!!.repliesDao().getReplyByExternalId("external-1")
        assertThat(reply?.syncState, equalTo(ReplyEntity.SYNC_STATE_FAILED))
        assertThat(reply?.nextRetryAt, equalTo(null as Long?))
        assertThat(reply?.lastSendError, equalTo("Send timed out."))
    }

    // The retry timer fires after a real (wall-clock) backoff delay, and the subsequent send
    // touches Room on its own executor. Virtual-time (StandardTestDispatcher) can't deterministically
    // await that cross-thread Room continuation, so these tests use a real dispatcher and short
    // backoff windows, polling for the outcome with a generous timeout.
    private suspend fun awaitUntil(timeoutMillis: Long = 5_000L, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            kotlinx.coroutines.delay(15)
        }
        throw AssertionError("Condition was not met within ${timeoutMillis}ms")
    }

    @Test
    fun retryTimerSendsQueuedReplyWhenBackoffElapsesWithoutExternalTrigger() = runBlocking {
        val sendCount = AtomicInteger(0)
        val recordingHub = object : UnsupportedEngageApiService() {
            override suspend fun sendConversationReply(
                conversationId: String,
                body: SendConversationReplyRequest,
            ): Response<okhttp3.ResponseBody> {
                sendCount.incrementAndGet()
                return Response.success("".toResponseBody(null))
            }
        }
        val conversationsRepository = ConversationsRepository(
            engageApiService = recordingHub,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        seedConversation("conversation-1")
        val now = System.currentTimeMillis()
        seedReply(
            id = "local-1",
            conversationId = "conversation-1",
            externalId = "external-1",
            createdAt = now,
            retryCount = 1,
            nextRetryAt = now + 200L,
        )

        // The flush finds the reply mid-backoff: nothing is sent, but a ~200ms timer is armed.
        conversationsRepository.requestFlush()
        assertThat(sendCount.get(), equalTo(0))

        // With no further poll, sync, or user action, the in-memory timer fires the retry on its own.
        awaitUntil {
            database!!.repliesDao().getReplyByExternalId("external-1")?.syncState ==
                ReplyEntity.SYNC_STATE_SENT
        }
        assertThat(sendCount.get(), equalTo(1))
        conversationsRepository.close()
    }

    @Test
    fun retryTimerReArmsToGlobalSoonestAcrossConversations() = runBlocking {
        val sends = java.util.Collections.synchronizedList(mutableListOf<String>())
        val recordingHub = object : UnsupportedEngageApiService() {
            override suspend fun sendConversationReply(
                conversationId: String,
                body: SendConversationReplyRequest,
            ): Response<okhttp3.ResponseBody> {
                sends += conversationId
                return Response.success("".toResponseBody(null))
            }
        }
        val conversationsRepository = ConversationsRepository(
            engageApiService = recordingHub,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        seedConversation("conversation-early")
        seedConversation("conversation-late")
        val now = System.currentTimeMillis()
        // Staggered backoffs in different conversations, spread wide (150ms vs 2s) on purpose: a CI
        // scheduling hiccup must not be able to make both replies due in the same flush pass. If they
        // could both come due at once, send order would fall back to iteration order rather than the
        // timer's soonest-first re-arm, and this test would be asserting luck. The wide gap means the
        // ordering below is genuinely caused by the timer arming to the global soonest then re-arming.
        seedReply(
            id = "local-late",
            conversationId = "conversation-late",
            externalId = "external-late",
            createdAt = now,
            retryCount = 1,
            nextRetryAt = now + 2_000L,
        )
        seedReply(
            id = "local-early",
            conversationId = "conversation-early",
            externalId = "external-early",
            createdAt = now,
            retryCount = 1,
            nextRetryAt = now + 150L,
        )

        conversationsRepository.requestFlush()

        // The timer arms to the soonest (early) and fires it while the late reply is still deep in its
        // backoff window — proving soonest-first selection, not a both-due single pass.
        awaitUntil { sends.contains("conversation-early") }
        assertThat(sends.toList(), equalTo(listOf("conversation-early")))
        assertThat(
            database!!.repliesDao().getReplyByExternalId("external-late")?.syncState,
            equalTo(ReplyEntity.SYNC_STATE_QUEUED),
        )

        // After the early send the timer re-arms to the late reply's deadline and fires it with no
        // external trigger.
        awaitUntil { sends.size == 2 }
        assertThat(sends.toList(), equalTo(listOf("conversation-early", "conversation-late")))
        conversationsRepository.close()
    }

    @Test
    fun closeCancelsPendingRetryTimer() = runBlocking {
        val sendCount = AtomicInteger(0)
        val recordingHub = object : UnsupportedEngageApiService() {
            override suspend fun sendConversationReply(
                conversationId: String,
                body: SendConversationReplyRequest,
            ): Response<okhttp3.ResponseBody> {
                sendCount.incrementAndGet()
                return Response.success("".toResponseBody(null))
            }
        }
        val conversationsRepository = ConversationsRepository(
            engageApiService = recordingHub,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        seedConversation("conversation-1")
        val now = System.currentTimeMillis()
        seedReply(
            id = "local-1",
            conversationId = "conversation-1",
            externalId = "external-1",
            createdAt = now,
            retryCount = 1,
            nextRetryAt = now + 300L,
        )

        // Arm the ~300ms timer, then tear down before it can fire.
        conversationsRepository.requestFlush()
        conversationsRepository.close()

        // Well past the backoff: a live timer would have fired by now, but close() cancelled it.
        kotlinx.coroutines.delay(700L)
        assertThat(sendCount.get(), equalTo(0))
    }

    @Test
    fun appLevelSyncFlushesQueuedRepliesAcrossAllConversations() = runBlocking {
        val conversationsRepository = buildConversationsRepository()
        val conversationsSync = buildConversationsSync(conversationsRepository)

        // The queued reply lives in a conversation that the forward-sync page does NOT return,
        // proving the app-level sync flushes queued replies app-wide (conversationId = null),
        // not just for whatever conversation the sync happens to page in.
        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-queued",
                subject = "",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )
        database!!.repliesDao().upsertReply(
            ReplyEntity(
                id = "local-1",
                conversationID = "conversation-queued",
                senderType = ReplyEntity.SENDER_TYPE_FAN,
                participantID = null,
                externalID = "external-1",
                createdAt = System.currentTimeMillis(),
                content = listOf(ReplyContentBlock(ReplyContentBlock.TYPE_TEXT, "Hello", null)),
                syncState = ReplyEntity.SYNC_STATE_QUEUED,
                retryCount = 2,
                nextRetryAt = 0L,
                lastSendError = "network",
            )
        )
        doReturn(Response.success<Void>(202, null))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-queued"), any())
        doReturn(Response.success(conversationsPage(nextCursor = null, before = null, hasMore = false).toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversations(cursor = null, before = null)

        conversationsSync.sync()

        verify(mockEngageApiService).sendConversationReply(eq("conversation-queued"), any())
        val reply = database!!.repliesDao().getRepliesForConversation("conversation-queued").first()
        assertThat(reply.syncState, equalTo(ReplyEntity.SYNC_STATE_SENT))
        assertThat(reply.retryCount, equalTo(0))
        assertThat(reply.nextRetryAt, equalTo(null as Long?))
    }

    /**
     * A reply send that suspends mid-flight (gated on [release]) holds the actor. A second flush
     * triggered while the first is in-flight must NOT also send the reply: the first pass marks it
     * sent, so the second pass finds nothing queued. Proves single-flight prevents double-sends.
     */
    @Test
    fun concurrentFlushesDoNotDoubleSendQueuedReply() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sendCount = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()
        val gatedHub = object : UnsupportedEngageApiService() {
            override suspend fun sendConversationReply(
                conversationId: String,
                body: SendConversationReplyRequest,
            ): Response<okhttp3.ResponseBody> {
                sendCount.incrementAndGet()
                release.await()
                return Response.success("".toResponseBody(null))
            }
        }
        val conversationsRepository = ConversationsRepository(
            engageApiService = gatedHub,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
            backgroundDispatcher = testDispatcher,
        )
        seedQueuedReply()

        val first = launch { conversationsRepository.requestFlush() }
        advanceUntilIdle() // first pass is now suspended inside the gated send

        val second = launch { conversationsRepository.requestFlush() }
        advanceUntilIdle() // second flush is enqueued but the actor is busy with the first pass

        release.complete(Unit)
        advanceUntilIdle()
        first.join()
        second.join()

        assertThat(sendCount.get(), equalTo(1))
        val reply = database!!.repliesDao().getRepliesForConversation("conversation-1").first()
        assertThat(reply.syncState, equalTo(ReplyEntity.SYNC_STATE_SENT))
        conversationsRepository.close()
    }

    /**
     * Several flushes triggered while a pass is in-flight collapse into a single follow-up pass
     * rather than one pass each. Proves the actor coalesces concurrent triggers.
     */
    @Test
    fun concurrentTriggersCoalesceIntoOneFollowUpPass() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sendCount = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()
        val gatedHub = object : UnsupportedEngageApiService() {
            override suspend fun sendConversationReply(
                conversationId: String,
                body: SendConversationReplyRequest,
            ): Response<okhttp3.ResponseBody> {
                sendCount.incrementAndGet()
                release.await()
                return Response.success("".toResponseBody(null))
            }
        }
        val conversationsRepository = ConversationsRepository(
            engageApiService = gatedHub,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
            backgroundDispatcher = testDispatcher,
        )
        seedQueuedReply()

        val first = launch { conversationsRepository.requestFlush() }
        advanceUntilIdle() // pass #1 in-flight, gated
        assertThat(conversationsRepository.flushPassCount, equalTo(1))

        // Three more triggers arrive while pass #1 is held; they must coalesce.
        val followers = listOf(
            launch { conversationsRepository.requestFlush() },
            launch { conversationsRepository.requestFlush() },
            launch { conversationsRepository.requestFlush() },
        )
        advanceUntilIdle()

        release.complete(Unit)
        advanceUntilIdle()
        first.join()
        followers.forEach { it.join() }

        // Pass #1 (in-flight) plus exactly one coalesced follow-up for the three later triggers.
        assertThat(conversationsRepository.flushPassCount, equalTo(2))
        assertThat(sendCount.get(), equalTo(1))
        conversationsRepository.close()
    }

    @Test
    fun markConversationReadSuccessUpdatesConversationFromServerCheckpoint() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "Support",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )

        doReturn(Response.success("""
            {
              "conversationID": "conversation-1",
              "lastReadAt": "2024-01-01T00:00:03Z",
              "lastReadReplyID": "reply-server"
            }
        """.trimIndent().toResponseBody(null)))
            .whenever(mockEngageApiService)
            .markConversationRead(eq("conversation-1"), any())

        conversationsRepository.markConversationRead(
            conversationId = "conversation-1",
            lastReadReplyId = "reply-1",
        )

        verify(mockEngageApiService).markConversationRead(eq("conversation-1"), any())
        val updatedConversation = database!!.conversationsDao().getConversationById("conversation-1")
        assertThat(updatedConversation?.lastReadAt, equalTo(1704067203000L))
        assertThat(updatedConversation?.lastReadReplyID, equalTo("reply-server"))
    }

    @Test
    fun markConversationReadUsesServerConversationIdAsAuthoritativePersistenceKey() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-request",
                subject = "Request",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )
        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-server",
                subject = "Server",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )

        doReturn(Response.success("""
            {
              "conversationID": "conversation-server",
              "lastReadAt": "2024-01-01T00:00:05Z",
              "lastReadReplyID": null
            }
        """.trimIndent().toResponseBody(null)))
            .whenever(mockEngageApiService)
            .markConversationRead(eq("conversation-request"), any())

        conversationsRepository.markConversationRead(
            conversationId = "conversation-request",
            lastReadReplyId = null,
        )

        assertThat(
            database!!.conversationsDao().getConversationById("conversation-request")?.lastReadAt,
            equalTo(null as Long?)
        )
        assertThat(
            database!!.conversationsDao().getConversationById("conversation-server")?.lastReadAt,
            equalTo(1704067205000L)
        )
    }

    @Test
    fun markConversationReadRejectsResponseThatOmitsCheckpointFields() {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        runBlocking {
            database!!.conversationsDao().upsertConversation(
                ConversationItem(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = "2024-01-01T00:00:01Z",
                    lastIncomingReplyAt = null,
                    lastIncomingParticipantID = null,
                    lastReadAt = null,
                    lastReadReplyID = null,
                    lastReplyPreview = null,
                    createdAt = "2024-01-01T00:00:00Z",
                    participantIDs = emptyList(),
                    updatedAt = "2024-01-01T00:00:01Z",
                ).toEntity()
            )
        }

        runBlocking {
            doReturn(Response.success("""
                {
                  "conversationID": "conversation-1"
                }
            """.trimIndent().toResponseBody(null)))
                .whenever(mockEngageApiService)
                .markConversationRead(eq("conversation-1"), any())
        }

        val thrown = assertThrows(Throwable::class.java) {
            runBlocking {
                conversationsRepository.markConversationRead(
                    conversationId = "conversation-1",
                    lastReadReplyId = null,
                )
            }
        }
        assertThat(thrown is IllegalStateException || thrown.cause != null, equalTo(true))
    }

    @Test
    fun conversationsSyncRejectsMissingIncludedParticipantsEnvelope() {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        runBlocking {
            doReturn(
                Response.success(
                    """
                    {
                      "conversations": [],
                      "nextCursor": null,
                      "nextBefore": null,
                      "hasMore": false
                    }
                    """.trimIndent().toResponseBody(null)
                )
            ).whenever(mockEngageApiService)
                .getConversations(include = "participants", cursor = null, before = null)
        }

        assertThrows(com.squareup.moshi.JsonDataException::class.java) {
            runBlocking {
                buildConversationsSync(conversationsRepository).triggerEagerBackwardHistoryBackfill()
            }
        }
    }

    @Test
    fun fakeSyncRepliesReconcilesPendingReplyByExternalId() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "",
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = emptyList(),
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )

        doReturn(Response.success<Void>(202, null))
            .whenever(mockEngageApiService)
            .sendConversationReply(eq("conversation-1"), any())

        conversationsRepository.sendReply(
            conversationId = "conversation-1",
            message = "Hello",
            externalId = "external-1",
        )

        conversationsRepository.fakeSyncReplies(
            conversationId = "conversation-1",
            replies = listOf(
                ReplyItem(
                    id = "reply-1",
                    conversationID = "conversation-1",
                    senderType = "fan",
                    participantID = null,
                    externalID = "external-1",
                    createdAt = "2024-01-01T00:00:02Z",
                    content = listOf(ReplyContentBlockItem.text("Hello")),
                )
            ),
            forwardCursor = null,
            backwardCursor = null,
        )

        assertThat(
            database!!.repliesDao().getRepliesForConversation("conversation-1").map { it.id },
            equalTo(listOf("reply-1"))
        )
    }

    @Test
    fun conversationPushUpsertsConversationParticipantAndDedupesReplyByExternalId() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "Support",
                participantIDs = emptyList(),
                lastReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingReplyAt = "2024-01-01T00:00:01Z",
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:01Z",
            ).toEntity()
        )

        database!!.repliesDao().upsertReply(
            ReplyItem(
                id = "external-1",
                conversationID = "conversation-1",
                senderType = "fan",
                participantID = null,
                content = listOf(ReplyContentBlockItem.text("Pending")),
                externalID = "external-1",
                createdAt = "2024-01-01T00:00:00Z",
            ).toEntity()
        )

        conversationsRepository.saveConversationPushPayload(
            conversation = ConversationItem(
                id = "conversation-1",
                subject = "Support",
                participantIDs = listOf("participant-1"),
                lastReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:02Z",
            ),
            reply = ReplyItem(
                id = "reply-1",
                conversationID = "conversation-1",
                senderType = "participant",
                participantID = "participant-1",
                externalID = "external-1",
                content = listOf(ReplyContentBlockItem.text("Hello")),
                createdAt = "2024-01-01T00:00:02Z",
            ),
            participant = ParticipantItem(
                id = "participant-1",
                name = "Casey Jones",
                avatarURL = null,
                updatedAt = "2024-01-01T00:00:00Z",
            ),
        )

        val savedConversation = database!!.conversationsDao().getConversationById("conversation-1")
        assertThat(savedConversation?.lastIncomingReplyAt, equalTo(1704067202000L))

        val savedParticipant = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(savedParticipant?.name, equalTo("Casey Jones"))

        val savedReplies = database!!.repliesDao().getRepliesForConversation("conversation-1")
        assertThat(savedReplies.map { it.id }, equalTo(listOf("reply-1")))
    }

    @Test
    fun conversationPushWithNullParticipantFieldsPreservesExistingParticipant() = runBlocking {
        val conversationsRepository = buildConversationsRepository()

        database!!.participantsDao().upsertParticipant(
            ParticipantItem(
                id = "participant-1",
                name = "Casey Jones",
                avatarURL = "https://example.com/casey.png",
                updatedAt = "2024-01-01T00:00:00Z",
            ).toEntity()
        )

        // The push payload's participant can arrive with null name/avatar when the server-side
        // member profile lookup misses; it must not degrade the good local row.
        conversationsRepository.saveConversationPushPayload(
            conversation = ConversationItem(
                id = "conversation-1",
                subject = "Support",
                participantIDs = listOf("participant-1"),
                lastReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:02Z",
            ),
            reply = ReplyItem(
                id = "reply-1",
                conversationID = "conversation-1",
                senderType = "participant",
                participantID = "participant-1",
                externalID = null,
                content = listOf(ReplyContentBlockItem.text("Hello")),
                createdAt = "2024-01-01T00:00:02Z",
            ),
            participant = ParticipantItem(
                id = "participant-1",
                name = null,
                avatarURL = null,
                updatedAt = "2024-01-02T00:00:00Z",
            ),
        )

        val savedParticipant = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(savedParticipant?.name, equalTo("Casey Jones"))
        assertThat(savedParticipant?.avatarUrl, equalTo("https://example.com/casey.png"))
    }

    @Test
    fun conversationPushPreservesExistingReadStateWhenLastReadAtMissingFromPayload() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "Support",
                participantIDs = listOf("participant-1"),
                lastReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingParticipantID = null,
                lastReadAt = "2024-01-01T00:00:02Z",
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:02Z",
            ).toEntity()
        )

        conversationsRepository.saveConversationPushPayload(
            conversation = ConversationItem(
                id = "conversation-1",
                subject = "Support",
                participantIDs = listOf("participant-1"),
                lastReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = "2024-01-01T00:00:00Z",
                updatedAt = "2024-01-01T00:00:02Z",
            ),
            reply = ReplyItem(
                id = "reply-1",
                conversationID = "conversation-1",
                senderType = "participant",
                participantID = "participant-1",
                externalID = null,
                content = listOf(ReplyContentBlockItem.text("Hello")),
                createdAt = "2024-01-01T00:00:02Z",
            ),
            participant = ParticipantItem(
                id = "participant-1",
                name = "Casey Jones",
                avatarURL = null,
                updatedAt = "2024-01-01T00:00:00Z",
            ),
        )

        val savedConversation = database!!.conversationsDao().getConversationById("conversation-1")
        assertThat(savedConversation?.lastReadAt, equalTo(1704067202000L))

        val hasUnread = savedConversation?.lastIncomingReplyAt?.let { lastIncomingReplyAt ->
            savedConversation.lastReadAt == null || lastIncomingReplyAt > savedConversation.lastReadAt
        } ?: false
        assertThat(hasUnread, equalTo(false))
    }

    @Test
    fun triggerEagerBackwardHistoryBackfillRunsForwardThenBackfillAndSetsStickyCompletion() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        doReturn(
            Response.success(
                conversationsPage(
                    nextCursor = "forward-1",
                    before = "back-1",
                    hasMore = true,
                    participantName = "Alex Mason",
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = null, before = null)
        doReturn(
            Response.success(
                conversationsPage(
                    nextCursor = "forward-2",
                    before = "back-2",
                    hasMore = false,
                    participantName = "Taylor Mason",
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = "forward-1", before = null)
        doReturn(
            Response.success(
                conversationsPage(
                    nextCursor = null,
                    before = "back-3",
                    hasMore = true,
                    participantName = "Taylor Mason",
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = null, before = "back-2")
        doReturn(
            Response.success(
                conversationsPage(
                    nextCursor = null,
                    before = null,
                    hasMore = false,
                    participantName = "Taylor Mason",
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = null, before = "back-3")

        buildConversationsSync(conversationsRepository).triggerEagerBackwardHistoryBackfill()

        val conversationsState = database!!.syncStateDao().getSyncState("conversations")
        assertThat(conversationsState?.forwardCursor, equalTo("forward-2"))
        assertThat(conversationsState?.backwardCursor, equalTo(null))
        assertThat(conversationsState?.historyComplete, equalTo(true))
        val syncedParticipant = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(syncedParticipant?.name, equalTo("Taylor Mason"))

        inOrder(mockEngageApiService) {
            verify(mockEngageApiService).getConversations(cursor = null, before = null)
            verify(mockEngageApiService).getConversations(cursor = "forward-1", before = null)
            verify(mockEngageApiService).getConversations(cursor = null, before = "back-2")
            verify(mockEngageApiService).getConversations(cursor = null, before = "back-3")
        }

        doReturn(Response.success(conversationsPage(nextCursor = "forward-3", before = null, hasMore = false).toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversations(cursor = "forward-2", before = null)

        buildConversationsSync(conversationsRepository).triggerEagerBackwardHistoryBackfill()

        val stickyState = database!!.syncStateDao().getSyncState("conversations")
        assertThat(stickyState?.historyComplete, equalTo(true))
    }

    @Test
    fun externalHubResetCancelsActiveEagerBackwardHistoryBackfill() = runTest {
        val backfillRequestStarted = CompletableDeferred<Unit>()
        val backfillRequestCancelled = AtomicBoolean(false)
        val fakeEngageApiService = object : UnsupportedEngageApiService() {
            override suspend fun getConversations(
                include: String?,
                cursor: String?,
                before: String?,
            ): Response<okhttp3.ResponseBody> {
                backfillRequestStarted.complete(Unit)
                return try {
                    CompletableDeferred<Response<okhttp3.ResponseBody>>().await()
                } catch (cancelled: CancellationException) {
                    backfillRequestCancelled.set(true)
                    throw cancelled
                }
            }

            override suspend fun getPosts(deviceIdentifier: String, cursor: String?): Response<okhttp3.ResponseBody> =
                Response.error(410, "gone".toResponseBody(null))
        }
        val hubSyncCoordinator = buildFullHubSyncCoordinator(fakeEngageApiService)
        val conversationsRepository = ConversationsRepository(
            hubSyncCoordinator = hubSyncCoordinator,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        val conversationsSync = ConversationsSync(
            conversationsRepository = conversationsRepository,
            hubSyncCoordinator = hubSyncCoordinator,
            conversationsDao = database!!.conversationsDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        hubSyncCoordinator.registerResetCancellable(conversationsSync)

        val backfill = async {
            conversationsSync.triggerEagerBackwardHistoryBackfill()
        }
        backfillRequestStarted.await()

        val resetRequired = try {
            hubSyncCoordinator.getPosts(deviceIdentifier = "device", cursor = null)
            throw AssertionError("Expected posts request to require reset")
        } catch (resetRequired: SyncResetRequiredException) {
            resetRequired
        }
        resetRequired.resetHandler.resetAfterSyncCancellation()

        try {
            backfill.await()
            throw AssertionError("Expected active backfill to be cancelled")
        } catch (_: CancellationException) {
            // Expected.
        }
        assertThat(backfillRequestCancelled.get(), equalTo(true))
    }

    @Test
    fun repositoryHubResetCancelsActiveStandaloneConversationsSync() = runTest {
        val syncRequestStarted = CompletableDeferred<Unit>()
        val syncRequestCancelled = AtomicBoolean(false)
        val fakeEngageApiService = object : UnsupportedEngageApiService() {
            override suspend fun getConversations(
                include: String?,
                cursor: String?,
                before: String?,
            ): Response<okhttp3.ResponseBody> {
                try {
                    return suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation {
                            syncRequestCancelled.set(true)
                        }
                        syncRequestStarted.complete(Unit)
                    }
                } finally {
                    syncRequestCancelled.set(true)
                }
            }

            override suspend fun markConversationRead(
                conversationId: String,
                body: MarkConversationReadRequest,
            ): Response<okhttp3.ResponseBody> =
                Response.error(410, "gone".toResponseBody(null))
        }
        val hubSyncCoordinator = buildFullHubSyncCoordinator(fakeEngageApiService)
        val conversationsRepository = ConversationsRepository(
            hubSyncCoordinator = hubSyncCoordinator,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        val conversationsSync = ConversationsSync(
            conversationsRepository = conversationsRepository,
            hubSyncCoordinator = hubSyncCoordinator,
            conversationsDao = database!!.conversationsDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        hubSyncCoordinator.registerResetCancellable(conversationsSync)

        val sync = async {
            conversationsSync.sync()
        }
        syncRequestStarted.await()

        val error = try {
            conversationsRepository.markConversationRead("conversation-1", lastReadReplyId = "reply-1")
            throw AssertionError("Expected markConversationRead 410 to fail with reset error")
        } catch (error: IllegalStateException) {
            error
        }

        var rootCause: Throwable = error
        while (rootCause.cause != null) {
            rootCause = rootCause.cause!!
        }
        assertThat(rootCause is SyncResetRequiredException, equalTo(true))
        try {
            sync.await()
            throw AssertionError("Expected active conversations sync to be cancelled")
        } catch (_: CancellationException) {
            // Expected.
        }
        assertThat(syncRequestCancelled.get(), equalTo(true))
    }

    @Test
    fun eagerBackwardHistoryBackfillOriginated410DoesNotSelfCancelReset() = runTest {
        val fakeEngageApiService = object : UnsupportedEngageApiService() {
            override suspend fun getConversations(
                include: String?,
                cursor: String?,
                before: String?,
            ): Response<okhttp3.ResponseBody> =
                Response.error(410, "gone".toResponseBody(null))
        }
        val hubSyncCoordinator = buildFullHubSyncCoordinator(fakeEngageApiService)
        val conversationsRepository = ConversationsRepository(
            hubSyncCoordinator = hubSyncCoordinator,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        val conversationsSync = ConversationsSync(
            conversationsRepository = conversationsRepository,
            hubSyncCoordinator = hubSyncCoordinator,
            conversationsDao = database!!.conversationsDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        hubSyncCoordinator.registerResetCancellable(conversationsSync)
        seedConversationData()

        val error = try {
            conversationsSync.triggerEagerBackwardHistoryBackfill()
            throw AssertionError("Expected backfill 410 to fail with reset error")
        } catch (error: IllegalStateException) {
            error
        }

        assertThat(error.cause is SyncResetRequiredException, equalTo(true))
        assertThat(database!!.conversationsDao().getConversationById("conversation-1"), equalTo(null as ConversationEntity?))
    }

    @Test
    fun triggerEagerBackwardHistoryBackfillMarksHistoryCompleteWhenNoBackwardCursorExists() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        doReturn(
            Response.success(
                conversationsPage(
                    nextCursor = null,
                    before = null,
                    hasMore = false,
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = null, before = null)

        buildConversationsSync(conversationsRepository).triggerEagerBackwardHistoryBackfill()

        val conversationsState = database!!.syncStateDao().getSyncState("conversations")
        assertThat(conversationsState?.backwardCursor, equalTo(null))
        assertThat(conversationsState?.historyComplete, equalTo(true))
    }

    @Test
    fun triggerEagerBackwardHistoryBackfillPersistsConversationFromRealishEngagePayload() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        doReturn(
            Response.success(
                realishConversationsPage(
                    nextBefore = "back-1",
                    hasMore = true,
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = null, before = null)
        doReturn(
            Response.success(
                realishConversationsPage(
                    conversationId = "conversation-older",
                    nextBefore = null,
                    hasMore = false,
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = null, before = "back-1")

        buildConversationsSync(conversationsRepository).triggerEagerBackwardHistoryBackfill()

        val syncedConversation = database!!.conversationsDao().getConversationById("conversation-1")
        assertThat(syncedConversation?.subject, equalTo("Support"))
        assertThat(syncedConversation?.participantIDs, equalTo(emptyList()))

        val conversationsState = database!!.syncStateDao().getSyncState("conversations")
        assertThat(conversationsState?.backwardCursor, equalTo(null))
        assertThat(conversationsState?.historyComplete, equalTo(true))
    }

    @Test
    fun syncRunsForwardConversationsOnly() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        val conversationsSync = buildConversationsSync(conversationsRepository)

        conversationsRepository.fakeSyncConversations(
            conversations = emptyList(),
            participants = emptyList(),
            forwardCursor = "forward-start",
            backwardCursor = "backward-start",
            historyComplete = false,
        )

        doReturn(
            Response.success(
                conversationsPage(
                    nextCursor = null,
                    before = null,
                    hasMore = false,
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = "forward-start", before = null)

        doReturn(
            Response.success(
                conversationsPage(
                    nextCursor = null,
                    before = null,
                    hasMore = false,
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = null, before = "backward-start")

        conversationsSync.sync()

        verify(mockEngageApiService).getConversations(cursor = "forward-start", before = null)
        verify(mockEngageApiService, never()).getConversations(cursor = null, before = "backward-start")

        val conversationsState = database!!.syncStateDao().getSyncState("conversations")
        assertThat(conversationsState?.forwardCursor, equalTo(null))
        assertThat(conversationsState?.backwardCursor, equalTo("backward-start"))
        assertThat(conversationsState?.historyComplete, equalTo(false))
    }

    @Test
    fun syncPreservesCompletedHistoryStateOnForwardRefresh() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )
        val conversationsSync = buildConversationsSync(conversationsRepository)

        conversationsRepository.fakeSyncConversations(
            conversations = emptyList(),
            participants = emptyList(),
            forwardCursor = "forward-start",
            backwardCursor = "backward-start",
            historyComplete = true,
        )

        doReturn(
            Response.success(
                conversationsPage(
                    nextCursor = "forward-next",
                    before = null,
                    hasMore = false,
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversations(cursor = "forward-start", before = null)

        conversationsSync.sync()

        val conversationsState = database!!.syncStateDao().getSyncState("conversations")
        assertThat(conversationsState?.forwardCursor, equalTo("forward-next"))
        assertThat(conversationsState?.backwardCursor, equalTo("backward-start"))
        assertThat(conversationsState?.historyComplete, equalTo(true))
    }

    @Test
    fun bootstrapLoadOlderAndPollingEvolveReplySyncStateWithPerThreadKey() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
            backgroundDispatcher = testDispatcher,
            pollingIntervalMs = 60_000,
        )

        doReturn(
            Response.success(
                repliesPage(
                    nextCursor = "thread-forward-1",
                    before = "thread-back-1",
                    hasMore = true,
                    participantName = "Alex Mason",
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversationReplies(conversationId = "conversation-1", cursor = null, before = null)
        doReturn(
            Response.success(
                repliesPage(
                    nextCursor = "thread-forward-2",
                    before = "thread-back-2",
                    hasMore = true,
                    participantName = "Jordan Mason",
                ).toResponseBody(null)
            )
        )
            .whenever(mockEngageApiService)
            .getConversationReplies(conversationId = "conversation-1", cursor = "thread-forward-1", before = null)
        doReturn(Response.success(repliesPage(nextCursor = "thread-forward-2", before = null, hasMore = false).toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversationReplies(conversationId = "conversation-1", cursor = null, before = "thread-back-1")

        conversationsRepository.bootstrapLatestReplies("conversation-1")

        val bootstrapState = database!!.syncStateDao().getSyncState("replies:conversation-1")
        assertThat(bootstrapState?.forwardCursor, equalTo("thread-forward-1"))
        assertThat(bootstrapState?.backwardCursor, equalTo("thread-back-1"))
        assertThat(bootstrapState?.historyComplete, equalTo(false))

        conversationsRepository.runRepliesForwardSyncIteration("conversation-1")

        val afterPollingState = database!!.syncStateDao().getSyncState("replies:conversation-1")
        assertThat(afterPollingState?.forwardCursor, equalTo("thread-forward-2"))
        assertThat(afterPollingState?.backwardCursor, equalTo("thread-back-2"))
        val syncedParticipant = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(syncedParticipant?.id, equalTo("participant-1"))
        assertThat(syncedParticipant?.name, equalTo(null as String?))

        conversationsRepository.loadOlderReplies(
            conversationId = "conversation-1",
            beforeReplyId = "reply-1",
            beforeCursor = "thread-back-1",
        )

        val afterOlderState = database!!.syncStateDao().getSyncState("replies:conversation-1")
        assertThat(afterOlderState?.backwardCursor, equalTo(null))
        assertThat(afterOlderState?.historyComplete, equalTo(true))
    }

    @Test
    fun runRepliesForwardSyncIteration410ClearsOnlyConversationData() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        seedConversationData()
        seedPostsAndSubscriptions()
        database!!.syncStateDao().upsertSyncState(
            io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity(
                roverEntity = "replies:conversation-1",
                forwardCursor = "thread-forward-1",
                backwardCursor = "thread-back-1",
                historyComplete = false,
            )
        )

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversationReplies(conversationId = "conversation-1", cursor = "thread-forward-1", before = null)

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                conversationsRepository.runRepliesForwardSyncIteration("conversation-1")
            }
        }

        assertConversationDataCleared()
        assertPostsAndSubscriptionsUntouched()
    }

    @Test
    fun startRepliesForwardPolling410StopsPollingJobs() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        seedConversationData()
        database!!.syncStateDao().upsertSyncState(
            io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity(
                roverEntity = "replies:conversation-1",
                forwardCursor = "thread-forward-1",
                backwardCursor = null,
                historyComplete = false,
            )
        )

        conversationsRepository.startRepliesForwardPolling("conversation-1")
        assertThat(conversationsRepository.hasRepliesPollingJob("conversation-1"), equalTo(true))

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversationReplies(any(), anyOrNull(), anyOrNull())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                conversationsRepository.runRepliesForwardSyncIteration("conversation-1")
            }
        }

        assertThat(conversationsRepository.hasRepliesPollingJob("conversation-1"), equalTo(false))
    }

    @Test
    fun bootstrapRepliesSynthesizesMissingParticipantReferences() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        doReturn(
            Response.success(
                repliesPage(
                    nextCursor = null,
                    before = null,
                    hasMore = false,
                    participantId = "participant-missing",
                    includeParticipants = false,
                ).toResponseBody(null)
            )
        ).whenever(mockEngageApiService)
            .getConversationReplies(conversationId = "conversation-1", cursor = null, before = null)

        conversationsRepository.bootstrapLatestReplies("conversation-1")

        val storedReplies = database!!.repliesDao().getRepliesForConversation("conversation-1")
        assertThat(storedReplies.size, equalTo(1))
        assertThat(storedReplies.first().participantID, equalTo("participant-missing"))

        val storedParticipant = database!!.participantsDao().getParticipantById("participant-missing")
        assertThat(storedParticipant?.id, equalTo("participant-missing"))
    }

    @Test
    fun bootstrapRepliesPlaceholderParticipantDoesNotDowngradeExistingRow() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        database!!.participantsDao().upsertParticipant(
            ParticipantItem(
                id = "participant-1",
                name = "Casey Jones",
                avatarURL = "https://example.com/casey.png",
                updatedAt = "2024-01-01T00:00:00Z",
            ).toEntity()
        )

        // The replies page references participant-1 but carries no participant records;
        // ensureParticipantsExist only inserts placeholders for missing rows and must not
        // downgrade the existing good row to a null-field placeholder.
        doReturn(
            Response.success(
                repliesPage(
                    nextCursor = null,
                    before = null,
                    hasMore = false,
                    participantId = "participant-1",
                    includeParticipants = false,
                ).toResponseBody(null)
            )
        ).whenever(mockEngageApiService)
            .getConversationReplies(conversationId = "conversation-1", cursor = null, before = null)

        conversationsRepository.bootstrapLatestReplies("conversation-1")

        val storedParticipant = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(storedParticipant?.name, equalTo("Casey Jones"))
        assertThat(storedParticipant?.avatarUrl, equalTo("https://example.com/casey.png"))
    }

    @Test
    fun bootstrapRepliesPreservesParticipantIdWhenReplyPayloadOmitsParticipants() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        doReturn(
            Response.success(
                repliesPage(
                    nextCursor = null,
                    before = null,
                    hasMore = false,
                    participantId = "participant-1",
                    includeParticipants = false,
                ).toResponseBody(null)
            )
        ).whenever(mockEngageApiService)
            .getConversationReplies(conversationId = "conversation-1", cursor = null, before = null)

        conversationsRepository.bootstrapLatestReplies("conversation-1")

        val storedReplies = database!!.repliesDao().getRepliesForConversation("conversation-1")
        assertThat(storedReplies.size, equalTo(1))
        assertThat(storedReplies.first().participantID, equalTo("participant-1"))

        val storedParticipant = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(storedParticipant?.id, equalTo("participant-1"))
    }

    @Test
    fun bootstrapLatestRepliesAcceptsApiReplyContentFieldShape() = runBlocking {
        val conversationsRepository = ConversationsRepository(
            engageApiService = mockEngageApiService,
            conversationsDao = database!!.conversationsDao(),
            repliesDao = database!!.repliesDao(),
            participantsDao = database!!.participantsDao(),
            syncStateDao = database!!.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database!!),
        )

        doReturn(
            Response.success(
                realishRepliesPage(
                    nextBefore = "reply-back-1",
                    hasMore = true,
                ).toResponseBody(null)
            )
        ).whenever(mockEngageApiService)
            .getConversationReplies(conversationId = "conversation-1", cursor = null, before = null)

        conversationsRepository.bootstrapLatestReplies("conversation-1")

        val storedReplies = database!!.repliesDao().getRepliesForConversation("conversation-1")
        assertThat(storedReplies.map { it.id }, equalTo(listOf("reply-1")))
        assertThat(storedReplies.first().createdAt, equalTo(1719835891000L))

        val repliesState = database!!.syncStateDao().getSyncState("replies:conversation-1")
        assertThat(repliesState?.backwardCursor, equalTo("reply-back-1"))
        assertThat(repliesState?.historyComplete, equalTo(false))
    }

    private fun conversationsPage(
        nextCursor: String?,
        before: String?,
        hasMore: Boolean,
        participantName: String = "Alex Mason",
    ): String {
        return """
            {
              "conversations": [
                {
                  "id": "conversation-1",
                  "subject": "Support",
                  "participantIDs": ["participant-1"],
                  "lastReplyAt": "2024-01-01T00:00:02Z",
                  "updatedAt": "2024-01-01T00:00:02Z",
                  "lastIncomingReplyAt": "2024-01-01T00:00:02Z",
                  "lastReadAt": "2024-01-01T00:00:01Z",
                  "lastReadReplyID": "reply-1",
                  "lastReplyPreview": "Hello",
                  "createdAt": "2024-01-01T00:00:00Z"
                }
              ],
              "included": {
                "participants": [
                  {
                    "id": "participant-1",
                    "name": "$participantName",
                    "avatarURL": null,
                    "updatedAt": "2024-01-01T00:00:00Z"
                  }
                ]
              },
              "nextCursor": ${nextCursor.toJson()},
              "nextBefore": ${before.toJson()},
              "hasMore": $hasMore
            }
        """.trimIndent()
    }

    private fun realishConversationsPage(
        conversationId: String = "conversation-1",
        nextBefore: String?,
        hasMore: Boolean,
    ): String {
        return """
            {
              "conversations": [
                {
                  "id": "$conversationId",
                  "subject": "Support",
                  "participantIDs": [],
                  "lastReplyAt": "2024-07-01T12:31:00Z",
                  "updatedAt": "2024-07-01T12:31:00Z",
                  "createdAt": "2024-07-01T12:00:00Z",
                  "lastIncomingReplyAt": "2024-07-01T12:15:00Z",
                  "lastReadAt": "2024-07-01T12:10:00Z",
                  "lastReadReplyID": "reply-1",
                  "lastReplyPreview": "Hello"
                }
              ],
              "included": { "participants": [] },
              "nextCursor": null,
              "nextBefore": ${nextBefore.toJson()},
              "hasMore": $hasMore
            }
        """.trimIndent()
    }

    private fun repliesPage(
        nextCursor: String?,
        before: String?,
        hasMore: Boolean,
        participantId: String = "participant-1",
        includeParticipants: Boolean = true,
        participantName: String = "Alex Mason",
    ): String {
        val resolvedParticipantName = participantName
        val replyParticipantId = if (includeParticipants) participantId else participantId

        return """
            {
              "replies": [
                {
                  "id": "reply-${nextCursor ?: before ?: "tail"}",
                  "conversationID": "conversation-1",
                  "senderType": "participant",
                  "participantID": ${replyParticipantId.toJson()},
                  "content": [{"type": "text", "text": "Hello from $resolvedParticipantName"}],
                  "externalID": null,
                  "createdAt": "2024-01-01T00:00:02Z"
                }
              ],
              "nextCursor": ${nextCursor.toJson()},
              "nextBefore": ${before.toJson()},
              "hasMore": $hasMore
            }
        """.trimIndent()
    }

    private fun realishRepliesPage(
        nextBefore: String?,
        hasMore: Boolean,
    ): String {
        return """
            {
              "replies": [
                {
                  "id": "reply-1",
                  "conversationID": "conversation-1",
                  "senderType": "participant",
                  "participantID": "participant-1",
                  "content": [{"type": "text", "text": "Hello"}],
                  "externalID": null,
                  "createdAt": "2024-07-01T12:11:31Z"
                }
              ],
              "nextCursor": null,
              "nextBefore": ${nextBefore.toJson()},
              "hasMore": $hasMore
            }
        """.trimIndent()
    }

    private fun String?.toJson(): String {
        return if (this == null) {
            "null"
        } else {
            "\"$this\""
        }
    }

    private suspend fun seedConversationData() {
        database!!.conversationsDao().upsertConversation(
            ConversationItem(
                id = "conversation-1",
                subject = "Support",
                lastReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingReplyAt = "2024-01-01T00:00:02Z",
                lastIncomingParticipantID = null,
                lastReadAt = "2024-01-01T00:00:01Z",
                lastReadReplyID = "reply-1",
                lastReplyPreview = "Hello",
                createdAt = "2024-01-01T00:00:00Z",
                participantIDs = listOf("participant-1"),
                updatedAt = "2024-01-01T00:00:02Z",
            ).toEntity()
        )

        database!!.participantsDao().upsertParticipant(
            ParticipantItem(
                id = "participant-1",
                name = "Casey Jones",
                avatarURL = null,
                updatedAt = "2024-01-01T00:00:00Z",
            ).toEntity()
        )

        database!!.repliesDao().upsertReply(
            ReplyItem(
                id = "reply-1",
                conversationID = "conversation-1",
                senderType = "participant",
                participantID = "participant-1",
                content = listOf(ReplyContentBlockItem.text("Hello")),
                externalID = null,
                createdAt = "2024-01-01T00:00:02Z",
            ).toEntity()
        )

        database!!.syncStateDao().upsertSyncState(
            io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity(
                roverEntity = "conversations",
                forwardCursor = "forward-conversations",
                backwardCursor = "backward-conversations",
                historyComplete = true,
            )
        )
        database!!.syncStateDao().upsertSyncState(
            io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity(
                roverEntity = "replies:conversation-1",
                forwardCursor = "forward-replies",
                backwardCursor = "backward-replies",
                historyComplete = false,
            )
        )
    }

    private suspend fun seedPostsAndSubscriptions() {
        val subscription = createTestSubscription(id = "subscription-1", name = "Sub 1", optIn = true, status = "published")
        database!!.subscriptionsDao().upsertSubscription(subscription.toEntity())
        database!!.postsDao().upsertPost(
            createTestPost(
                id = "post-1",
                subject = "Post 1",
                subscriptionID = subscription.id,
            ).toEntity()
        )
    }

    private suspend fun assertConversationDataCleared() {
        assertThat(database!!.conversationsDao().getAllConversations(), equalTo(emptyList()))
        assertThat(database!!.repliesDao().getRepliesForConversation("conversation-1"), equalTo(emptyList()))
        assertThat(database!!.participantsDao().getAllParticipants(), equalTo(emptyList()))
        assertThat(database!!.syncStateDao().getSyncState("conversations"), equalTo(null as io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity?))
        assertThat(database!!.syncStateDao().getSyncState("replies:conversation-1"), equalTo(null as io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity?))
    }

    private suspend fun assertPostsAndSubscriptionsUntouched() {
        assertThat(database!!.postsDao().getAllPosts().first().map { it.id }, equalTo(listOf("post-1")))
        assertThat(database!!.subscriptionsDao().getSubscriptionById("subscription-1")?.id, equalTo("subscription-1"))
    }

}
