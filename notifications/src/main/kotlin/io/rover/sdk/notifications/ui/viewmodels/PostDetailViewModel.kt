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

package io.rover.sdk.notifications.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostEntity
import io.rover.sdk.notifications.communicationhub.data.repository.CommHubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class PostDetailViewModel(
    private val postsRepository: CommHubRepository,
    private val postId: String
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    init {
        loadPost()
        markAsRead()
    }

    private fun loadPost() {
        viewModelScope.launch {
            try {
                // Fast path: check DAO first
                log.d("Loading post $postId from DAO")
                val post = postsRepository.getPostById(postId)
                if (post != null) {
                    log.d("Post $postId found in DAO")
                    _uiState.value = _uiState.value.copy(
                        post = post,
                        isLoading = false
                    )
                } else {
                    // Slow path: trigger sync and retry
                    log.d("Post $postId not found in DAO, triggering sync")
                    _uiState.value = _uiState.value.copy(isLoading = true)
                    
                    val syncSuccess = postsRepository.sync()
                    if (syncSuccess) {
                        log.d("Sync completed successfully, retrying DAO lookup for post $postId")
                        val postAfterSync = postsRepository.getPostById(postId)
                        if (postAfterSync != null) {
                            log.d("Post $postId found after sync")
                            _uiState.value = _uiState.value.copy(
                                post = postAfterSync,
                                isLoading = false
                            )
                        } else {
                            log.w("Post $postId still not found after successful sync")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Post not found"
                            )
                        }
                    } else {
                        log.e("Sync failed while trying to load post $postId")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Unable to load post. Please check your connection and try again."
                        )
                    }
                }
            } catch (exception: Exception) {
                log.e("Exception while loading post $postId: ${exception.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message
                )
            }
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
}

data class PostDetailUiState(
    val post: PostEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)