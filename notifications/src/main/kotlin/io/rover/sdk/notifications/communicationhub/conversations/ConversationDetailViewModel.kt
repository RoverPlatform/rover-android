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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
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
    val composerTextState: TextFieldState = TextFieldState()
    private val _isLoadingOlder = MutableStateFlow(false)
    private val _isSending = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _lastReadReplyId = MutableStateFlow<String?>(null)
    private val _loadState = MutableStateFlow(ConversationLoadState.Loading)
    private val _threadData = MutableStateFlow(ThreadData.empty())
    private val _effects = Channel<ConversationDetailEffect>(Channel.BUFFERED)
    // Tracks the last replyId argument processed by onLatestReplyVisible to deduplicate calls.
    private var _lastSubmittedVisibleReplyId: String? = null
    private var resetVersionObserverStarted = false

    val effects: Flow<ConversationDetailEffect> = _effects.receiveAsFlow()

    val uiState: StateFlow<ConversationDetailUiState> = combine(
        _isLoadingOlder,
        _isSending,
        _error,
        _lastReadReplyId,
        _loadState,
    ) { isLoadingOlder, isSending, error, lastReadReplyId, loadState ->
        ScreenState(
            isLoadingOlder = isLoadingOlder,
            isSending = isSending,
            error = error,
            lastReadReplyId = lastReadReplyId,
            loadState = loadState,
        )
    }.combine(_threadData) { screen, threadData ->
            ConversationDetailUiState(
                title = threadData.title,
                participantAvatarUrl = threadData.participantAvatarUrl,
                participantBio = threadData.participantBio,
                replies = threadData.replies,
                threadRows = threadData.threadRows,
                phase = ConversationDetailPhase.from(
                    hasConversation = threadData.conversationExists,
                    loadState = screen.loadState,
                ),
                isLoadingOlder = screen.isLoadingOlder,
                canLoadOlder = threadData.beforeCursor != null,
                isSending = screen.isSending,
            error = screen.error,
            lastReadReplyId = screen.lastReadReplyId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ConversationDetailUiState()
    )

    init {
        startResetVersionDismissObserverIfNeeded(conversationsRepository.getResetVersionFlow().value)

        observeThread(activeConversationId)
        viewModelScope.launch {
            try {
                if (!conversationsRepository.hasConversation(activeConversationId)) {
                    conversationsSync.triggerEagerBackwardHistoryBackfill()
                    // Check again after backfill; report it missing if it is still not there.
                    if (!conversationsRepository.hasConversation(activeConversationId)) {
                        reportNotFound("Conversation not found")
                        return@launch
                    }
                }
                startShowingIfNeeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("Failed to look up conversation: ${e.message}")
                reportLoadFailed(e.message ?: "Failed to load messages")
            }
        }
    }

    /**
     * Bootstraps the replies and starts the forward poll, once per conversation.
     *
     * Every state but two starts: an answer that has arrived is worth acting on however the
     * previous attempt ended. [ConversationLoadState.Showing] is already started, and the init
     * lookup and a late arrival can both reach here for one conversation, where a second
     * [ConversationsDataSource.startRepliesForwardPolling] would cancel the poll the first
     * started. [ConversationLoadState.Disconnected] is terminal: the store can no longer reach the
     * screen, so starting work against it would only settle the screen on a spinner nothing can
     * clear.
     */
    private fun startShowingIfNeeded() {
        when (_loadState.value) {
            ConversationLoadState.Showing, ConversationLoadState.Disconnected -> return
            ConversationLoadState.Loading,
            ConversationLoadState.NotFound,
            ConversationLoadState.Failed -> Unit
        }
        _loadState.value = ConversationLoadState.Showing
        // The store has since answered the question the error was reporting on.
        _error.value = null
        viewModelScope.launch {
            try {
                conversationsRepository.bootstrapLatestReplies(activeConversationId)
                conversationsRepository.startRepliesForwardPolling(activeConversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("Failed to bootstrap conversation replies: ${e.message}")
                _error.value = e.message ?: "Failed to load messages"
            }
        }
    }

    /**
     * The conversation was looked for and is not there.
     *
     * The strongest of the three outcomes below, and the only one entitled to tell the user the
     * conversation does not exist, so it is the only one that needs the lookup to have finished.
     */
    private fun reportNotFound(message: String) {
        reportLookupOutcome(ConversationLoadState.NotFound, message)
    }

    /**
     * An attempt threw. Says nothing about whether the conversation exists, and leaves the screen
     * able to recover: the store's flow is still live and an arrival still starts the screen.
     */
    private fun reportLoadFailed(message: String) {
        reportLookupOutcome(ConversationLoadState.Failed, message)
    }

    /**
     * The store's flow to this screen has failed, which nothing can undo.
     *
     * Recorded whether or not a conversation is on screen, unlike the two below: it is not a
     * statement about obtaining the conversation, which a conversation already here would settle,
     * but about the thread that is here no longer being able to change.
     */
    private fun reportDisconnected(message: String?) {
        _loadState.value = ConversationLoadState.Disconnected
        _error.value = message
    }

    /**
     * How the attempt to obtain the conversation ended: not there, or it threw.
     *
     * Two things both callers need and neither should have to remember. A conversation in the
     * store outranks either, since the phase reads the store first and one that arrived while the
     * attempt was failing is the newer answer. And [ConversationLoadState.Disconnected] is
     * terminal: a later failure describes the same broken screen and must not downgrade it to a
     * claim the earlier one did not make.
     */
    private fun reportLookupOutcome(outcome: ConversationLoadState, message: String?) {
        if (_threadData.value.conversationExists) return
        if (_loadState.value == ConversationLoadState.Disconnected) return
        _loadState.value = outcome
        _error.value = message
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
                    // The thread stands as it was: catch completes the collector, so discarding
                    // what is on screen would leave the phase reading an empty store with nothing
                    // able to fill it again. A screen that never got that far reports a load
                    // failure instead of spinning forever.
                    reportDisconnected(throwable.message)
                }
                .collect { data ->
                    _threadData.value = data
                    _lastReadReplyId.value = data.lastReadReplyId
                    // Whatever brings the conversation in — this screen's own backfill, a later
                    // sync, or a push — the screen starts when it lands.
                    if (data.conversationExists) startShowingIfNeeded()
                }
        }
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
        val message = composerTextState.text.toString().trim()
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
                composerTextState.clearText()
            } catch (throwable: Throwable) {
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                composerTextState.clearText()
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
            conversationExists = conversation != null,
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
        val conversationExists: Boolean,
    ) {
        companion object {
            fun empty(): ThreadData {
                return ThreadData(
                    title = "Conversation",
                    participantAvatarUrl = null,
                    participantBio = null,
                    replies = emptyList(),
                    threadRows = emptyList(),
                    beforeCursor = null,
                    lastReadReplyId = null,
                    conversationExists = false,
                )
            }
        }
    }

    private data class ScreenState(
        val isLoadingOlder: Boolean,
        val isSending: Boolean,
        val error: String?,
        val lastReadReplyId: String?,
        val loadState: ConversationLoadState,
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
    val phase: ConversationDetailPhase = ConversationDetailPhase.Loading,
    val isLoadingOlder: Boolean = false,
    val canLoadOlder: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val lastReadReplyId: String? = null,
)

internal sealed interface ConversationDetailEffect {
    data object Dismiss : ConversationDetailEffect
}
