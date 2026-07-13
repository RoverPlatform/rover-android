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






import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationDetailViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun existingModeBootstrapsStartsPollingAndShowsTranscript() = runTest {
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 2000L,
                    lastReadAt = 1500L,
                    lastReadReplyID = "reply-1",
                    createdAt = 1000L,
                    participantIDs = listOf("participant-1"),
                )
            )
            participantsFlow.value = listOf(
                ConversationTestFixtures.participant(id = "participant-1", name = "Morgan Lee")
            )
            repliesFlow.value = listOf(
                ConversationTestFixtures.reply(id = "reply-1", conversationId = "conversation-1", participantId = "participant-1", createdAt = 1000L),
                ConversationTestFixtures.reply(id = "reply-2", conversationId = "conversation-1", participantId = "participant-1", createdAt = 2000L),
                ConversationTestFixtures.reply(id = "reply-other", conversationId = "conversation-2", participantId = "participant-1", createdAt = 3000L),
            )
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.replies.map { it.id }, equalTo(listOf("reply-1", "reply-2")))
        assertThat(viewModel.uiState.value.lastReadReplyId, equalTo("reply-1"))
        assertThat(conversationsDataSource.bootstrapCalls, equalTo(listOf("conversation-1")))
        assertThat(conversationsDataSource.startPollingCalls, equalTo(listOf("conversation-1")))

        viewModel.onClearedForTest()
        assertThat(conversationsDataSource.stopPollingCalls, equalTo(listOf("conversation-1")))
    }

    @Test
    fun existingModeDismissesWhenResetVersionAdvances() = runTest {
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 2000L,
                    createdAt = 1000L,
                    participantIDs = emptyList(),
                )
            )
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )
        val dismissEffect = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            viewModel.effects.first()
        }

        advanceUntilIdle()

        assertThat(dismissEffect.isCompleted, equalTo(false))

        conversationsDataSource.resetVersionFlow.value = 1L
        advanceUntilIdle()

        assertThat(dismissEffect.isCompleted, equalTo(true))
        assertThat(dismissEffect.await(), equalTo(ConversationDetailEffect.Dismiss))
    }

    @Test
    fun existingModeUsesPersistedLastReadReplyCheckpointDirectly() = runTest {
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 2000L,
                    lastReadAt = 1500L,
                    lastReadReplyID = "reply-2",
                    createdAt = 1000L,
                    participantIDs = listOf("participant-1"),
                )
            )
            participantsFlow.value = listOf(
                ConversationTestFixtures.participant(id = "participant-1", name = "Morgan Lee")
            )
            repliesFlow.value = listOf(
                ConversationTestFixtures.reply(id = "reply-1", conversationId = "conversation-1", participantId = "participant-1", createdAt = 1000L),
                ConversationTestFixtures.reply(id = "reply-2", conversationId = "conversation-1", participantId = "participant-1", createdAt = 2000L),
            )
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.lastReadReplyId, equalTo("reply-2"))
    }

    @Test
    fun existingModeRendersNullParticipantRepliesAsOutgoingFanMessages() = runTest {
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 2000L,
                    createdAt = 1000L,
                    participantIDs = listOf("participant-1"),
                )
            )
            participantsFlow.value = listOf(
                ConversationTestFixtures.participant(id = "participant-1", name = "Morgan Lee")
            )
            repliesFlow.value = listOf(
                ConversationTestFixtures.reply(id = "reply-fan", conversationId = "conversation-1", participantId = null, createdAt = 1000L),
                ConversationTestFixtures.reply(id = "reply-participant", conversationId = "conversation-1", participantId = "participant-1", createdAt = 2000L),
            )
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        advanceUntilIdle()

        assertThat(
            viewModel.uiState.value.replies.map { it.id to it.isOutgoing },
            equalTo(listOf("reply-fan" to true, "reply-participant" to false))
        )
    }

    @Test
    fun loadOlderRequestsUsesOldestReplyAndBeforeCursor() = runTest {
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 2000L,
                    createdAt = 1000L,
                    participantIDs = emptyList(),
                )
            )
            repliesFlow.value = listOf(
                ConversationTestFixtures.reply(id = "reply-1", conversationId = "conversation-1", participantId = "participant-1", createdAt = 1000L),
                ConversationTestFixtures.reply(id = "reply-2", conversationId = "conversation-1", participantId = "participant-1", createdAt = 2000L),
            )
            backwardCursorFlow.value = "older-cursor"
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        advanceUntilIdle()
        viewModel.onLoadOlderRequested()
        advanceUntilIdle()

        assertThat(
            conversationsDataSource.loadOlderCalls,
            equalTo(
                listOf(
                    LoadOlderCall(
                        conversationId = "conversation-1",
                        beforeReplyId = "reply-1",
                        beforeCursor = "older-cursor",
                    )
                )
            )
        )
    }

    @Test
    fun latestReplyVisibleMarksReadOnlyWhenAdvanced() = runTest {
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 2000L,
                    lastReadAt = 1000L,
                    lastReadReplyID = "reply-1",
                    createdAt = 1000L,
                    participantIDs = emptyList(),
                )
            )
            repliesFlow.value = listOf(
                ConversationTestFixtures.reply(id = "reply-1", conversationId = "conversation-1", participantId = "participant-1", createdAt = 1000L),
                ConversationTestFixtures.reply(id = "reply-2", conversationId = "conversation-1", participantId = "participant-1", createdAt = 2000L),
            )
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        advanceUntilIdle()

        viewModel.onLatestReplyVisible("reply-1")
        advanceUntilIdle()
        assertThat(conversationsDataSource.markReadCalls, equalTo(emptyList()))

        viewModel.onLatestReplyVisible("reply-2")
        advanceUntilIdle()
        assertThat(
            conversationsDataSource.markReadCalls,
            equalTo(listOf(MarkReadCall("conversation-1", "reply-2")))
        )

        viewModel.onLatestReplyVisible("reply-2")
        advanceUntilIdle()
        assertThat(
            conversationsDataSource.markReadCalls,
            equalTo(listOf(MarkReadCall("conversation-1", "reply-2")))
        )
    }

    @Test
    fun onLatestReplyVisibleWithOptimisticFanReplyPassesNullToMarkRead() = runTest {
        // Bug: the SDK was sending the locally-generated UUID of a pending optimistic fan reply
        // to the server's mark-as-read endpoint, which rejected it with 400 because it had
        // never seen that ID. The fix: when the latest visible reply is a fan reply
        // (externalID != null), pass null instead, telling the server to mark everything read.
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 2000L,
                    createdAt = 1000L,
                    participantIDs = emptyList(),
                )
            )
            repliesFlow.value = listOf(
                // An optimistic fan reply: externalID is the locally-generated UUID
                ConversationTestFixtures.reply(
                    id = "optimistic-fan-uuid",
                    conversationId = "conversation-1",
                    participantId = null,
                    createdAt = 2000L,
                    externalID = "optimistic-fan-uuid",
                    syncState = ReplyEntity.SYNC_STATE_SENT,
                ),
            )
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        advanceUntilIdle()

        // Simulate the UI reporting the optimistic fan reply as visible
        viewModel.onLatestReplyVisible("optimistic-fan-uuid")
        advanceUntilIdle()

        // Must pass null, NOT the fan reply's locally-generated ID, to avoid a server 400
        assertThat(
            conversationsDataSource.markReadCalls,
            equalTo(listOf(MarkReadCall("conversation-1", null)))
        )
    }

    @Test
    fun onLatestReplyVisibleSendsLatestServerConfirmedParticipantReplyId() = runTest {
        // Happy path: when a visible reply has a server-confirmed participant reply earlier
        // in the list (externalID == null), that participant reply's ID is sent.
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 3000L,
                    createdAt = 1000L,
                    participantIDs = listOf("participant-1"),
                )
            )
            repliesFlow.value = listOf(
                // Participant reply (incoming, server-confirmed): externalID == null
                ConversationTestFixtures.reply(
                    id = "participant-reply-1",
                    conversationId = "conversation-1",
                    participantId = "participant-1",
                    createdAt = 1000L,
                    externalID = null,
                ),
                // Optimistic fan reply (outgoing, locally generated): externalID != null
                ConversationTestFixtures.reply(
                    id = "fan-reply-uuid",
                    conversationId = "conversation-1",
                    participantId = null,
                    createdAt = 2000L,
                    externalID = "fan-reply-uuid",
                    syncState = ReplyEntity.SYNC_STATE_SENT,
                ),
            )
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        advanceUntilIdle()

        // The fan reply is visible — it's the latest item shown to the user
        viewModel.onLatestReplyVisible("fan-reply-uuid")
        advanceUntilIdle()

        // Must send the participant reply's ID (the latest server-confirmed reply at or before
        // the visible one), not the fan reply's locally-generated UUID
        assertThat(
            conversationsDataSource.markReadCalls,
            equalTo(listOf(MarkReadCall("conversation-1", "participant-reply-1")))
        )
    }

    @Test
    fun bootstrapNetworkErrorDoesNotCrashAndSetsErrorState() = runTest {
        val conversationsDataSource = object : FakeConversationsDataSource() {
            override suspend fun bootstrapLatestReplies(conversationId: String) {
                throw IllegalStateException("Failed to sync replies. HTTP 502")
            }
        }.apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 2000L,
                    createdAt = 1000L,
                    participantIDs = emptyList(),
                )
            )
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        // Should complete without throwing — the coroutine must NOT propagate the exception
        advanceUntilIdle()

        // The error should be surfaced in the UI state, not crash the app
        assertThat(viewModel.uiState.value.error, equalTo("Failed to sync replies. HTTP 502"))
        assertThat(viewModel.uiState.value.isLoading, equalTo(false))
    }

    @Test
    fun existingSendNetworkErrorSurfacesInUiStateError() = runTest {
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 2000L,
                    createdAt = 1000L,
                    participantIDs = emptyList(),
                )
            )
            sendReplyError = java.net.ConnectException("Failed to connect to /10.0.2.2:8080")
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        viewModel.onComposerTextChanged("Hello")
        viewModel.onSendTapped()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error, equalTo("Failed to connect to /10.0.2.2:8080"))
        assertThat(viewModel.uiState.value.composerText, equalTo(""))
        assertThat(viewModel.uiState.value.isSending, equalTo(false))
    }

    @Test
    fun replyHelperUsesFanSenderTypeForOutgoingReplies() {
        val reply = ConversationTestFixtures.reply(
            id = "reply-1",
            conversationId = "conversation-1",
            participantId = null,
            createdAt = 1000L,
        )

        assertThat(reply.senderType, equalTo("fan"))
    }

    @Test
    fun headerUsesSubjectThenParticipantNamesThenConversationFallback() = runTest {
        val conversationsDataSource = FakeConversationsDataSource().apply {
            conversationsFlow.value = listOf(
                ConversationTestFixtures.conversation(
                    id = "conversation-1",
                    subject = "Support",
                    lastReplyAt = 1000L,
                    createdAt = 0L,
                    participantIDs = listOf("participant-1", "participant-2"),
                )
            )
            participantsFlow.value = listOf(
                ConversationTestFixtures.participant(id = "participant-1", name = "Morgan Lee", avatarUrl = "  ", bio = "Concert concierge"),
                ConversationTestFixtures.participant(id = "participant-2", name = "Taylor Chen", avatarUrl = "https://example.com/avatar.jpg", bio = "VIP support"),
            )
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationId = "conversation-1",
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.title, equalTo("Support"))
        assertThat(viewModel.uiState.value.participantAvatarUrl, equalTo(null))
        assertThat(viewModel.uiState.value.participantBio, equalTo("Concert concierge"))

        conversationsDataSource.conversationsFlow.value = listOf(
            ConversationTestFixtures.conversation(
                id = "conversation-1",
                subject = "   ",
                lastReplyAt = 1000L,
                createdAt = 0L,
                participantIDs = listOf("participant-1", "participant-2"),
            )
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.title, equalTo("Morgan Lee, Taylor Chen"))

        conversationsDataSource.participantsFlow.value = emptyList()

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.title, equalTo("Conversation"))
        assertThat(viewModel.uiState.value.participantAvatarUrl, equalTo(null))
        assertThat(viewModel.uiState.value.participantBio, equalTo(null))
    }

    @Test
    fun missingConversation_triggersBackfillThenBootstrap_andIsLoadingStaysTrueUntilComplete() = runTest {
        val events = mutableListOf<String>()
        val conversationsDataSource = object : FakeConversationsDataSource() {
            private var hasConversationCallCount = 0

            override suspend fun hasConversation(conversationId: String): Boolean {
                events += "hasConversation"
                hasConversationCallCount++
                // First call: missing; second call (after backfill): found
                return hasConversationCallCount > 1
            }

            override suspend fun bootstrapLatestReplies(conversationId: String) {
                events += "bootstrapLatestReplies:$conversationId"
                super.bootstrapLatestReplies(conversationId)
            }
        }
        val conversationsSync = object : ConversationsHistorySync {
            override suspend fun triggerEagerBackwardHistoryBackfill() {
                events += "triggerBackfill"
            }

            override suspend fun syncForward() {
                events += "syncForward"
            }
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationsSync = conversationsSync,
            conversationId = "conversation-missing",
        )

        // isLoading should be true before the coroutine completes
        assertThat(viewModel.uiState.value.isLoading, equalTo(true))

        advanceUntilIdle()

        assertThat(
            events,
            equalTo(
                listOf(
                    "hasConversation",
                    "triggerBackfill",
                    "hasConversation",
                    "bootstrapLatestReplies:conversation-missing",
                )
            )
        )
        // Polling should have been started after bootstrap succeeds
        assertThat(conversationsDataSource.startPollingCalls, equalTo(listOf("conversation-missing")))
    }

    @Test
    fun missingConversation_showsErrorIfStillNotFoundAfterBackfill() = runTest {
        val conversationsDataSource = object : FakeConversationsDataSource() {
            override suspend fun hasConversation(conversationId: String): Boolean {
                // Return false on both checks (before and after backfill)
                return false
            }
        }
        val conversationsSync = object : ConversationsHistorySync {
            override suspend fun triggerEagerBackwardHistoryBackfill() {
                Unit
            }

            override suspend fun syncForward() {
                Unit
            }
        }

        val viewModel = ConversationDetailViewModel(
            conversationsRepository = conversationsDataSource,
            conversationsSync = conversationsSync,
            conversationId = "conversation-never-synced",
        )

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading, equalTo(false))
        assertThat(viewModel.uiState.value.error, equalTo("Conversation not found"))
        // Polling must NOT be started when conversation is not found
        assertThat(conversationsDataSource.startPollingCalls, equalTo(emptyList()))
    }

    @Test
    fun revealedClearsNotificationForExistingConversation() = runTest {
        val notificationPresenter = FakeConversationPushNotificationPresenter()
        val viewModel = ConversationDetailViewModel(
            conversationsRepository = FakeConversationsDataSource(),
            conversationId = "conversation-1",
            conversationNotificationPresenter = notificationPresenter,
        )

        viewModel.revealed()
        advanceUntilIdle()

        assertThat(notificationPresenter.clearedConversationIds, equalTo(listOf("conversation-1")))
    }

    private open class FakeConversationsDataSource : ConversationsDataSource {
        val conversationsFlow = MutableStateFlow<List<ConversationEntity>>(emptyList())
        val participantsFlow = MutableStateFlow<List<ParticipantEntity>>(emptyList())
        val repliesFlow = MutableStateFlow<List<ReplyEntity>>(emptyList())
        val backwardCursorFlow = MutableStateFlow<String?>(null)
        val resetVersionFlow = MutableStateFlow(0L)

        val bootstrapCalls = mutableListOf<String>()
        val startPollingCalls = mutableListOf<String>()
        val stopPollingCalls = mutableListOf<String>()
        val loadOlderCalls = mutableListOf<LoadOlderCall>()
        val markReadCalls = mutableListOf<MarkReadCall>()
        var sendReplyError: Throwable? = null
        val sendReplyCalls = mutableListOf<SendReplyCall>()

        override suspend fun hasConversation(conversationId: String): Boolean = true

        override fun getConversationsFlow(): Flow<List<ConversationEntity>> = conversationsFlow

        override fun getParticipantsFlow(): Flow<List<ParticipantEntity>> = participantsFlow

        override fun getRepliesFlow(): Flow<List<ReplyEntity>> = repliesFlow

        override fun getResetVersionFlow(): StateFlow<Long> = resetVersionFlow

        override fun getRepliesBackwardCursorFlow(conversationId: String): Flow<String?> = backwardCursorFlow

        override suspend fun bootstrapLatestReplies(conversationId: String) {
            bootstrapCalls += conversationId
        }

        override fun startRepliesForwardPolling(conversationId: String) {
            startPollingCalls += conversationId
        }

        override fun stopRepliesForwardPolling(conversationId: String) {
            stopPollingCalls += conversationId
        }

        override suspend fun loadOlderReplies(
            conversationId: String,
            beforeReplyId: String?,
            beforeCursor: String?,
        ) {
            loadOlderCalls += LoadOlderCall(conversationId, beforeReplyId, beforeCursor)
        }

        override suspend fun markConversationRead(
            conversationId: String,
            lastReadReplyId: String?,
        ) {
            markReadCalls += MarkReadCall(conversationId, lastReadReplyId)
        }

        override suspend fun sendReply(
            conversationId: String,
            message: String,
            externalId: String?,
        ): String {
            sendReplyCalls += SendReplyCall(conversationId, message, externalId)
            sendReplyError?.let { throw it }
            return externalId ?: "generated-external-id"
        }
    }

    private data class LoadOlderCall(
        val conversationId: String,
        val beforeReplyId: String?,
        val beforeCursor: String?,
    )

    private data class MarkReadCall(
        val conversationId: String,
        val lastReadReplyId: String?,
    )

    private data class SendReplyCall(
        val conversationId: String,
        val message: String,
        val externalId: String?,
    )

    private class FakeConversationPushNotificationPresenter : ConversationPushNotificationPresenter {
        override val smallIconResId: Int = 0
        val clearedConversationIds = mutableListOf<String>()

        override suspend fun presentConversationNotification(
            conversationId: String,
            participantName: String?,
            participantAvatarUrl: String?,
            body: String,
        ) {
            Unit
        }

        override suspend fun clearConversationNotification(conversationId: String) {
            clearedConversationIds += conversationId
        }
    }

}
