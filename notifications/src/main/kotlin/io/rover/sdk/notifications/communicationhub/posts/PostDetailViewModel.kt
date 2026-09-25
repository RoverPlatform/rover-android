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

package io.rover.sdk.notifications.communicationhub.posts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.logging.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

internal class PostDetailViewModel(
    private val postsRepository: PostsRepository,
    private val syncCoordinator: SyncCoordinatorInterface,
    private val postId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    /** Set once the post is known to be in Room, so a post is marked read exactly once per screen. */
    private var markedRead = false

    init {
        observePost()
        fetchIfMissing()
    }

    /**
     * Room is the source of truth for what the screen shows. Whatever brings the row in, the sync
     * below, a later background sync or a push with a bundled post, the detail fills in when it
     * lands, and an error shown in the meantime is withdrawn.
     */
    private fun observePost() {
        viewModelScope.launch {
            postsRepository.observePostWithSubscriptionById(postId)
                .catch { exception ->
                    log.e("Observing post $postId failed: $exception")
                    updateWhileNoPostShown { copy(isLoading = false, error = exception.message ?: LOAD_FAILED) }
                }
                .collect { row -> row?.post?.let(::showPost) }
        }
    }

    /**
     * The one place a post reaches the state. Idempotent, and called from both coroutines: the
     * observer whenever Room emits the row, and the fetch whenever one of its own lookups finds
     * it, so a fetched post is shown even if the observed flow has failed and completed.
     */
    private fun showPost(post: PostEntity) {
        log.d("Post $postId is in Room, showing it")
        _uiState.value = _uiState.value.copy(
            post = post,
            isLoading = false,
            error = null,
        )
        if (!markedRead) {
            markedRead = true
            markAsRead()
        }
    }

    /**
     * A post that is not in Room yet (a deep link or push tap on a fresh install) is fetched by a
     * full sync. The outcome is judged by whether the post is present afterwards, not by the sync's
     * aggregate result: a sync reports [SyncCoordinatorInterface.Result.RetryNeeded] when any
     * participant fails, and the posts participant may well have succeeded in the same run.
     *
     * Every write here goes through [updateWhileNoPostShown]: the lookups suspend on IO, and by the
     * time a continuation resumes here [observePost] may already have shown the post, in which case
     * a stale "loading" or "not found" conclusion must not overwrite it.
     */
    private fun fetchIfMissing() {
        viewModelScope.launch {
            try {
                postsRepository.getPostById(postId)?.let { post ->
                    log.d("Post $postId found in DAO")
                    showIfNoPostShown(post)
                    return@launch
                }
                log.d("Post $postId not found in DAO, triggering sync")
                updateWhileNoPostShown { copy(isLoading = true) }

                val result = syncCoordinator.awaitSync()
                postsRepository.getPostById(postId)?.let { post ->
                    log.d("Post $postId found after sync")
                    showIfNoPostShown(post)
                    return@launch
                }
                if (result == SyncCoordinatorInterface.Result.Succeeded) {
                    log.w("Post $postId still not found after successful sync")
                    updateWhileNoPostShown { copy(isLoading = false, error = "Post not found") }
                } else {
                    log.w("Sync did not fully succeed while trying to load post $postId")
                    updateWhileNoPostShown { copy(isLoading = false, error = LOAD_FAILED) }
                }
            } catch (exception: Exception) {
                log.e("Exception while loading post $postId: ${exception.message}")
                updateWhileNoPostShown { copy(isLoading = false, error = exception.message ?: LOAD_FAILED) }
            }
        }
    }

    /**
     * The fetch's lookups return a snapshot taken on IO; by the time the continuation resumes here
     * the observer may have shown the row, possibly a newer one (read state, for instance). The
     * observer's row wins, so the snapshot is shown only while nothing is on screen yet.
     */
    private fun showIfNoPostShown(post: PostEntity) {
        if (_uiState.value.post == null) {
            showPost(post)
        }
    }

    /**
     * Check-and-write on the main thread, where both coroutines run, so it is atomic with respect
     * to [observePost]'s writes: once a post is shown, nothing the fetch concludes can hide it.
     */
    private fun updateWhileNoPostShown(update: PostDetailUiState.() -> PostDetailUiState) {
        val current = _uiState.value
        if (current.post == null) {
            _uiState.value = current.update()
        }
    }

    private fun markAsRead() {
        viewModelScope.launch {
            try {
                postsRepository.markPostAsRead(postId)
            } catch (exception: Exception) {
                log.e("Failed to mark post as read: ${exception}")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        /** Also the fallback for an exception without a message, so the error branch always renders. */
        const val LOAD_FAILED = "Unable to load post. Please check your connection and try again."
    }
}

data class PostDetailUiState(
    val post: PostEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)
