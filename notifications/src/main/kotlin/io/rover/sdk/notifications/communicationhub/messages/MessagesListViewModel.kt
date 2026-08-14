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

package io.rover.sdk.notifications.communicationhub.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.notifications.communicationhub.InboxSeenWatermark
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsHistorySync
import io.rover.sdk.notifications.communicationhub.conversations.badgeActivityAt
import io.rover.sdk.notifications.communicationhub.posts.PostsDataSource
import io.rover.sdk.core.logging.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


internal class MessagesListViewModel(
    private val postsRepository: PostsDataSource,
    private val conversationsRepository: ConversationsDataSource,
    private val conversationsSync: ConversationsHistorySync,
    private val syncCoordinator: SyncCoordinatorInterface,
    private val inboxSeenWatermark: InboxSeenWatermark,
    private val pollingIntervalMs: Long = 10_000L,
) : ViewModel() {
    private var pollingJob: Job? = null

    /**
     * Newest item activity seen in the most recent feed emission — the max over posts' `receivedAt`
     * and conversations' inbound-only `badgeActivityAt` — or null before the first emission.
     *
     * This is the value written to the seen watermark; see [InboxSeenWatermark.markSeen]. Tracked
     * separately from [_rows] because rows are search filtered, and a value derived from a filtered
     * list would under-report.
     */
    @Volatile
    private var newestActivityAt: Long? = null

    /**
     * Whether the messages list is currently in front of the user.
     *
     * Set on reveal and on a return to the foreground, cleared when the list is concealed —
     * mirroring the lifecycle hooks the Hub UI drives (see `Hub.kt`: first composition reveals,
     * ON_STOP/dispose conceals once, and an ON_START after an ON_STOP foregrounds).
     *
     * Volatile because it is written from the main-thread lifecycle callbacks and read by the feed
     * collector.
     */
    @Volatile
    private var isRevealed: Boolean = false
    private val _rows = MutableStateFlow<List<MessageFeedRow>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isExpanded = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(true)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * An observable UI state for the entire messages list UI.
     *
     * Android uses this pattern to better guarantee atomic UI updates.
     *
     * (this is equivalent to iOS using `objectWillChange` for all properties which then schedules
     * a single update. Here on Jetpack Compose you are required to arrange for atomicity yourself.)
     */
    val uiState: StateFlow<MessagesListUiState> = combine(
        _rows,
        _searchQuery,
        _isExpanded,
        _isLoading,
        _isRefreshing,
        _error
    ) { rows, searchQuery, isExpanded, isLoading, isRefreshing, error ->
        MessagesListUiState(
            rows = rows,
            searchQuery = searchQuery,
            isExpanded = isExpanded,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = MessagesListUiState()
    )

    init {
        observeMessagesList()
        refresh()
    }

    private fun observeMessagesList() {
        viewModelScope.launch {
            combine(
                postsRepository.getPostsFlow(),
                conversationsRepository.getConversationsFlow(),
                conversationsRepository.getParticipantsFlow(),
                conversationsRepository.getRepliesFlow(),
                _searchQuery,
            ) { posts, conversations, participants, replies, searchQuery ->
                MessagesFeedSnapshot(
                    rows = MessagesFeedBuilder.buildRows(
                        posts = posts,
                        conversations = conversations,
                        participants = participants,
                        replies = replies,
                        searchQuery = searchQuery,
                    ),
                    newestActivityAt = listOfNotNull(
                        posts.maxOfOrNull { it.post.receivedAt },
                        conversations.mapNotNull { it.badgeActivityAt }.maxOrNull(),
                    ).maxOrNull(),
                )
            }
                .flowOn(Dispatchers.Default)
                .catch { exception ->
                    _isLoading.value = false
                    _error.value = exception.message
                }
                .collect { snapshot ->
                    newestActivityAt = snapshot.newestActivityAt
                    if (isRevealed) {
                        // Mark on arrival: the user is looking at the list right now, so a post or
                        // conversation update that syncs in underneath them has been seen the
                        // moment it appears.
                        //
                        // The watermark only moves when it is handed an observed timestamp, so it
                        // cannot be pushed to "now" on reveal to cover items that land during the
                        // visit; absorbing them has to happen explicitly, here, as the store
                        // changes. Without this an item arriving mid-view would badge over the
                        // open list until the user navigated away — the same problem the
                        // foreground hook solves for the background/return case.
                        //
                        // markSeen is monotonic and null-tolerant, so a repeated or stale value
                        // costs nothing.
                        inboxSeenWatermark.markSeen(snapshot.newestActivityAt)
                    }
                    _rows.value = snapshot.rows
                    _isLoading.value = false
                    // note: deliberately not clearing _error here; rows re-emit on any database
                    // change, which would silently dismiss a refresh/backfill error. The error
                    // is cleared by a subsequent successful refresh() or by clearError().
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                _error.value = when (syncCoordinator.awaitSync()) {
                    SyncCoordinatorInterface.Result.Succeeded -> null
                    SyncCoordinatorInterface.Result.RetryNeeded -> "Failed to refresh messages"
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _error.value = exception.message ?: "Failed to refresh messages"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun markPostAsRead(postId: String) {
        viewModelScope.launch {
            try {
                postsRepository.markPostAsRead(postId)
            } catch (exception: Exception) {
                _error.value = exception.message
            }
        }
    }

    fun onMessagesListRevealed() {
        isRevealed = true
        markInboxSeen()

        viewModelScope.launch {
            try {
                conversationsSync.triggerEagerBackwardHistoryBackfill()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _error.value = exception.message ?: "Failed to load conversation history"
            }
        }
    }

    /**
     * The messages list has stopped being visible (backgrounded, or navigated away from).
     *
     * The watermark is advanced once more here to absorb the final feed emission, and the flag is
     * cleared so items arriving after the user has left badge normally.
     */
    fun onMessagesListConcealed() {
        isRevealed = false
        markInboxSeen()
    }

    /**
     * The messages list has come back into the foreground after an ON_STOP, without ever leaving
     * composition (the user backgrounded the app on the list and returned to it).
     *
     * [onMessagesListRevealed] does not re-run in this situation (it is bound to first
     * composition), so an item that arrived while backgrounded would badge over the open list
     * until the user navigated away. Deliberately narrower than the reveal handler: it advances the
     * watermark only, and must not re-trigger the conversations history backfill on every app
     * foreground.
     */
    fun onMessagesListForegrounded() {
        isRevealed = true
        markInboxSeen()
    }

    /**
     * Advances the inbox seen watermark, zeroing the badge.
     *
     * The watermark is set to the newest item activity we've observed — post `receivedAt` or
     * conversation inbound-only `badgeActivityAt` — because those are the same timestamps the
     * badge compares. It is null before the first feed emission (e.g. reveal racing the first
     * query) or when the inbox has no badgeable activity, in which case there is nothing to absorb
     * and the watermark stays put — the device clock is never written here.
     */
    private fun markInboxSeen() {
        inboxSeenWatermark.markSeen(newestActivityAt)
    }

    /**
     * Periodically re-runs the conversations forward sync while the messages list is on
     * screen, so new conversation activity appears without a manual refresh.
     *
     * The first sync is delayed by one interval rather than run immediately:
     * [onMessagesListRevealed] already performs a forward sync when the list appears.
     *
     * Poll failures are logged but deliberately not surfaced through the error state; a
     * transient failure on a background poll shouldn't interrupt the user.
     */
    fun startForwardPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(pollingIntervalMs)
                try {
                    conversationsSync.syncForward()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    log.e("Messages list forward sync poll failed: ${exception.message}")
                }
            }
        }
    }

    fun stopForwardPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        stopForwardPolling()
        super.onCleared()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    fun clearError() {
        _error.value = null
    }
}

/**
 * One emission of the combined messages feed: the rows to display, plus the newest item activity
 * in that emission (before search filtering) — max over posts' `receivedAt` and conversations'
 * inbound-only `badgeActivityAt`.
 */
private data class MessagesFeedSnapshot(
    val rows: List<MessageFeedRow>,
    val newestActivityAt: Long?,
)

internal data class MessagesListUiState(
    val rows: List<MessageFeedRow> = emptyList(),
    val searchQuery: String = "",
    val isExpanded: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)


/**
 * Extension function for combining 6 flows with individual parameters.
 *
 * (kotlin coroutines only contains arity 5, and we needed 6.)
 */
@Suppress("UNCHECKED_CAST")
private fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow1: kotlinx.coroutines.flow.Flow<T1>,
    flow2: kotlinx.coroutines.flow.Flow<T2>,
    flow3: kotlinx.coroutines.flow.Flow<T3>,
    flow4: kotlinx.coroutines.flow.Flow<T4>,
    flow5: kotlinx.coroutines.flow.Flow<T5>,
    flow6: kotlinx.coroutines.flow.Flow<T6>,
    transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): kotlinx.coroutines.flow.Flow<R> = combine(flow1, flow2, flow3, flow4, flow5, flow6) { flows ->
    transform(
        flows[0] as T1,
        flows[1] as T2,
        flows[2] as T3,
        flows[3] as T4,
        flows[4] as T5,
        flows[5] as T6
    )
}
