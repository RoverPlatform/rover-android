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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rover.sdk.core.logging.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class ConversationDetailViewModel(
    private val conversationsRepository: ConversationsDataSource,
    private val conversationsSync: ConversationsHistorySync = object : ConversationsHistorySync {
        override suspend fun triggerEagerBackwardHistoryBackfill() {
            Unit
        }

        override suspend fun syncForward() {
            Unit
        }
    },
    private val conversationId: String,
    private val conversationNotificationPresenter: ConversationPushNotificationPresenter =
        NoOpConversationPushNotificationPresenter,
) : ViewModel() {
    private val activeConversationId: String = conversationId
    private val _composerText = MutableStateFlow("")
    private val _isLoadingOlder = MutableStateFlow(false)
    private val _isSending = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _lastReadReplyId = MutableStateFlow<String?>(null)
    private val _threadData = MutableStateFlow(ThreadData.empty())
    private val _effects = Channel<ConversationDetailEffect>(Channel.BUFFERED)
    // Tracks the last replyId argument processed by onLatestReplyVisible to deduplicate calls.
    private var _lastSubmittedVisibleReplyId: String? = null
    private var resetVersionObserverStarted = false

    val effects: Flow<ConversationDetailEffect> = _effects.receiveAsFlow()

    val uiState: StateFlow<ConversationDetailUiState> = combine(
        _composerText,
        _isLoadingOlder,
        _isSending,
        _error,
        _lastReadReplyId,
    ) { composerText, isLoadingOlder, isSending, error, lastReadReplyId ->
        QuintState(
            composerText = composerText,
            isLoadingOlder = isLoadingOlder,
            isSending = isSending,
            error = error,
            lastReadReplyId = lastReadReplyId,
        )
    }.combine(_threadData) { quint, threadData ->
            ConversationDetailUiState(
                title = threadData.title,
                participantAvatarUrl = threadData.participantAvatarUrl,
                participantBio = threadData.participantBio,
                replies = threadData.replies,
                threadRows = threadData.threadRows,
                composerText = quint.composerText,
                isLoading = threadData.isLoading,
                isLoadingOlder = quint.isLoadingOlder,
                canLoadOlder = threadData.beforeCursor != null,
                isSending = quint.isSending,
            error = quint.error,
            lastReadReplyId = quint.lastReadReplyId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ConversationDetailUiState(
            isLoading = true,
        )
    )

    init {
        startResetVersionDismissObserverIfNeeded(conversationsRepository.getResetVersionFlow().value)

        observeThread(activeConversationId)
        viewModelScope.launch {
            try {
                val conversationExists = conversationsRepository.hasConversation(activeConversationId)
                if (!conversationExists) {
                    conversationsSync.triggerEagerBackwardHistoryBackfill()
                    // Check again after backfill; surface error if still missing
                    val existsAfterBackfill = conversationsRepository.hasConversation(activeConversationId)
                    if (!existsAfterBackfill) {
                        _threadData.value = ThreadData.empty(isLoading = false)
                        _error.value = "Conversation not found"
                        return@launch
                    }
                }
                conversationsRepository.bootstrapLatestReplies(activeConversationId)
                conversationsRepository.startRepliesForwardPolling(activeConversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("Failed to bootstrap conversation replies: ${e.message}")
                _threadData.value = _threadData.value.copy(isLoading = false)
                _error.value = e.message ?: "Failed to load messages"
            }
        }
    }

    private fun observeThread(conversationId: String) {
        viewModelScope.launch {
            combine(
                conversationsRepository.getConversationsFlow(),
                conversationsRepository.getParticipantsFlow(),
                conversationsRepository.getRepliesFlow(),
                conversationsRepository.getRepliesBackwardCursorFlow(conversationId),
            ) { conversations, participants, replies, beforeCursor ->
                buildThreadData(conversationId, conversations, participants, replies, beforeCursor)
            }
                .catch { throwable ->
                    log.e("Failed to observe thread: ${throwable.message}")
                    _threadData.value = ThreadData.empty(isLoading = false)
                    _error.value = throwable.message
                }
                .collect { data ->
                    _threadData.value = data.copy(isLoading = false)
                    _lastReadReplyId.value = data.lastReadReplyId
                }
        }
    }

    fun onComposerTextChanged(value: String) {
        _composerText.value = value
    }

    fun revealed() {
        viewModelScope.launch {
            try {
                conversationNotificationPresenter.clearConversationNotification(activeConversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("Failed to clear conversation notification: ${e.message}")
            }
        }
    }

    fun onSendTapped() {
        val message = _composerText.value.trim()
        if (message.isEmpty() || _isSending.value) return

        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            val externalId = java.util.UUID.randomUUID().toString()

            try {
                conversationsRepository.sendReply(
                    conversationId = activeConversationId,
                    message = message,
                    externalId = externalId,
                )
                _composerText.value = ""
            } catch (throwable: Throwable) {
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                _composerText.value = ""
                _error.value = throwable.message
            } finally {
                _isSending.value = false
            }
        }
    }

    fun onLoadOlderRequested() {
        val beforeReplyId = uiState.value.replies.firstOrNull()?.id
        val beforeCursor = _threadData.value.beforeCursor

        if (beforeCursor == null || _isLoadingOlder.value) return

        viewModelScope.launch {
            _isLoadingOlder.value = true
            try {
                conversationsRepository.loadOlderReplies(
                    conversationId = activeConversationId,
                    beforeReplyId = beforeReplyId,
                    beforeCursor = beforeCursor,
                )
            } catch (throwable: Throwable) {
                log.e("Failed to load older replies: ${throwable.message}")
                _error.value = throwable.message
            } finally {
                _isLoadingOlder.value = false
            }
        }
    }

    fun onLatestReplyVisible(replyId: String?) {
        if (replyId == null) return
        // Deduplicate: skip if this is the same visible reply position we already processed.
        if (replyId == _lastSubmittedVisibleReplyId) return

        // Use the latest server-confirmed reply ID as the read checkpoint.
        //
        // Fan replies are inserted into the local DB optimistically before the server confirms
        // them. During that window, the reply's local `id` is the client-generated idempotency
        // UUID (see sendReply), which the server has never seen as a reply ID. Passing it to
        // markConversationRead causes a server-side 400 because the server validates
        // lastReadReplyId against its own database.
        //
        // Server-confirmed replies have syncState == confirmed. The externalID fallback preserves
        // compatibility with rows created before syncState was modelled explicitly.
        val threadReplies = _threadData.value.replies
        val visibleIndex = threadReplies.indexOfFirst { it.id == replyId }
        val confirmedReplyId: String? = if (visibleIndex >= 0) {
            threadReplies
                .subList(0, visibleIndex + 1)
                .lastOrNull { it.syncState == ReplyEntity.SYNC_STATE_CONFIRMED || (!it.isOutgoing && it.externalID == null) }
                ?.id
        } else {
            null
        }

        // Skip if the server already knows about this read position (non-null match only;
        // a null confirmedReplyId means we have not found a server-confirmed reply yet
        // and should still notify the server to mark everything read).
        val currentLastReadReplyId = _lastReadReplyId.value
        if (confirmedReplyId != null && confirmedReplyId == currentLastReadReplyId) {
            _lastSubmittedVisibleReplyId = replyId
            return
        }

        val previousSubmittedVisibleReplyId = _lastSubmittedVisibleReplyId
        _lastSubmittedVisibleReplyId = replyId

        viewModelScope.launch {
            try {
                conversationsRepository.markConversationRead(
                    conversationId = activeConversationId,
                    lastReadReplyId = confirmedReplyId,
                )
                _lastReadReplyId.value = confirmedReplyId
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                _lastSubmittedVisibleReplyId = previousSubmittedVisibleReplyId
                log.e("Failed to mark conversation read: ${throwable.message}")
                _error.value = throwable.message
            }
        }
    }

    override fun onCleared() {
        conversationsRepository.stopRepliesForwardPolling(activeConversationId)
        _effects.close()
        super.onCleared()
    }

    fun onClearedForTest() {
        onCleared()
    }

    private fun startResetVersionDismissObserverIfNeeded(baselineResetVersion: Long) {
        if (resetVersionObserverStarted) return
        resetVersionObserverStarted = true

        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var previousResetVersion = baselineResetVersion
            conversationsRepository.getResetVersionFlow().collect { currentResetVersion ->
                if (currentResetVersion > previousResetVersion) {
                    _effects.trySend(ConversationDetailEffect.Dismiss)
                }
                previousResetVersion = currentResetVersion
            }
        }
    }

    private fun buildThreadData(
        conversationId: String,
        conversations: List<ConversationEntity>,
        participants: List<ParticipantEntity>,
        replies: List<ReplyEntity>,
        beforeCursor: String?,
    ): ThreadData {
        val conversation = conversations.firstOrNull { it.id == conversationId }
        val participantsById = participants.associateBy { it.id }
        val threadReplies = replies
            .filter { it.conversationID == conversationId }
            .sortedBy { it.createdAt }

        val title = resolveTitle(conversation, participantsById)
        val primaryParticipant = resolvePrimaryParticipant(conversation, participantsById)
        val rows = threadReplies.map { reply ->
            val participant = reply.participantID?.let(participantsById::get)
            val senderName = participant?.let {
                it.name?.trim()?.ifBlank { "Participant" } ?: "Participant"
            }
            reply.content
                .filter { block ->
                    !block.type.equals(ReplyContentBlock.TYPE_TEXT, ignoreCase = true) &&
                    !block.type.equals(ReplyContentBlock.TYPE_IMAGE, ignoreCase = true)
                }
                .forEach { block -> log.w("Unknown reply content block type '${block.type}' in reply ${reply.id}") }
            ConversationReplyRow(
                id = reply.id,
                senderId = reply.participantID,
                senderName = senderName,
                senderAvatarUrl = participant?.avatarUrl,
                content = reply.content,
                sentAt = reply.createdAt,
                externalID = reply.externalID,
                isOutgoing = reply.senderType == ReplyEntity.SENDER_TYPE_FAN,
                syncState = reply.syncState,
            )
        }
        val threadRows = buildConversationThreadRows(rows)

        return ThreadData(
            title = title,
            participantAvatarUrl = primaryParticipant?.avatarUrl?.trim()?.ifBlank { null },
            participantBio = primaryParticipant?.bio?.trim()?.ifBlank { null },
            replies = rows,
            threadRows = threadRows,
            beforeCursor = beforeCursor,
            lastReadReplyId = conversation?.lastReadReplyID,
            isLoading = false,
        )
    }

    private fun resolveTitle(
        conversation: ConversationEntity?,
        participantsById: Map<String, ParticipantEntity>,
    ): String {
        return resolveConversationTitle(conversation, participantsById)
    }

    private fun resolvePrimaryParticipant(
        conversation: ConversationEntity?,
        participantsById: Map<String, ParticipantEntity>,
    ): ParticipantEntity? {
        if (conversation == null) return null

        return conversation.participantIDs.orEmpty().mapNotNull { participantId ->
            participantsById[participantId]
        }.firstOrNull()
    }

    private data class ThreadData(
        val title: String,
        val participantAvatarUrl: String?,
        val participantBio: String?,
        val replies: List<ConversationReplyRow>,
        val threadRows: List<ConversationThreadRow>,
        val beforeCursor: String?,
        val lastReadReplyId: String?,
        val isLoading: Boolean,
    ) {
        companion object {
            fun empty(isLoading: Boolean = true): ThreadData {
                return ThreadData(
                    title = "Conversation",
                    participantAvatarUrl = null,
                    participantBio = null,
                    replies = emptyList(),
                    threadRows = emptyList(),
                    beforeCursor = null,
                    lastReadReplyId = null,
                    isLoading = isLoading,
                )
            }
        }
    }

    private data class QuintState(
        val composerText: String,
        val isLoadingOlder: Boolean,
        val isSending: Boolean,
        val error: String?,
        val lastReadReplyId: String?,
    )

    private object NoOpConversationPushNotificationPresenter : ConversationPushNotificationPresenter {
        override val smallIconResId: Int = 0

        override suspend fun presentConversationNotification(
            conversationId: String,
            participantName: String?,
            participantAvatarUrl: String?,
            body: String,
        ) {
            Unit
        }

        override suspend fun clearConversationNotification(conversationId: String) {
            Unit
        }
    }
}

internal data class ConversationReplyRow(
    val id: String,
    val senderId: String?,
    val senderName: String?,
    val senderAvatarUrl: String?,
    val content: List<ReplyContentBlock>,
    val sentAt: Long,
    val externalID: String?,
    val isOutgoing: Boolean,
    val syncState: String = ReplyEntity.SYNC_STATE_CONFIRMED,
)

internal data class ConversationDetailUiState(
    val title: String = "Conversation",
    val participantAvatarUrl: String? = null,
    val participantBio: String? = null,
    val replies: List<ConversationReplyRow> = emptyList(),
    val threadRows: List<ConversationThreadRow> = emptyList(),
    val composerText: String = "",
    val isLoading: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val canLoadOlder: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val lastReadReplyId: String? = null,
)

internal sealed interface ConversationDetailEffect {
    data object Dismiss : ConversationDetailEffect
}
