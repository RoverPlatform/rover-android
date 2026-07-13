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
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsHistorySync
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
    private val pollingIntervalMs: Long = 10_000L,
) : ViewModel() {
    private var pollingJob: Job? = null
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
                MessagesFeedBuilder.buildRows(
                    posts = posts,
                    conversations = conversations,
                    participants = participants,
                    replies = replies,
                    searchQuery = searchQuery,
                )
            }
                .flowOn(Dispatchers.Default)
                .catch { exception ->
                    _isLoading.value = false
                    _error.value = exception.message
                }
                .collect { rows ->
                    _rows.value = rows
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
