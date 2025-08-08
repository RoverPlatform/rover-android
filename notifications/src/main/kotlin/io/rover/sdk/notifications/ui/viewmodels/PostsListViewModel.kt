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
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostWithSubscription
import io.rover.sdk.notifications.communicationhub.data.repository.CommHubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale


internal class PostsListViewModel(
    private val repository: CommHubRepository
) : ViewModel() {
    
    private val _allPosts = MutableStateFlow<List<PostWithSubscription>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isExpanded = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(true)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    /**
     * An observable UI state for the entire posts list UI.
     *
     * Android uses this pattern to better guarantee atomic UI updates.
     *
     * (this is equivalent to iOS using `objectWillChange` for all properties which then schedules
     * a single update. Here on Jetpack Compose you are required to arrange for atomicity yourself.)
     */
    val uiState: StateFlow<PostsListUiState> = combine(
        _allPosts,
        _searchQuery,
        _isExpanded,
        _isLoading,
        _isRefreshing,
        _error
    ) { allPosts, searchQuery, isExpanded, isLoading, isRefreshing, error ->
        PostsListUiState(
            posts = filterPosts(allPosts, searchQuery),
            searchQuery = searchQuery,
            isExpanded = isExpanded,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            error = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = PostsListUiState()
    )

    init {
        loadPosts()
        refresh()
    }

    private fun loadPosts() {
        viewModelScope.launch {
            repository.getPostsFlow()
                .catch { exception ->
                    _isLoading.value = false
                    _error.value = exception.message
                }
                .collect { posts ->
                    _allPosts.value = posts
                    _isLoading.value = false
                    _error.value = null
                }
        }
    }
    
    private fun filterPosts(posts: List<PostWithSubscription>, query: String): List<PostWithSubscription> {
        if (query.isBlank()) return posts
        
        val lowercaseQuery = query.lowercase(Locale.getDefault())
        return posts.filter { postWithSubscription ->
            postWithSubscription.post.subject.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
            postWithSubscription.post.previewText.lowercase(Locale.getDefault()).contains(lowercaseQuery)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            
            try {
                val success = repository.sync()
                if (!success) {
                    _isRefreshing.value = false
                    _error.value = "Failed to sync posts"
                } else {
                    _isRefreshing.value = false
                    _error.value = null
                }
            } catch (exception: Exception) {
                _isRefreshing.value = false
                _error.value = exception.message
            }
        }
    }

    fun markPostAsRead(postId: String) {
        viewModelScope.launch {
            try {
                repository.markPostAsRead(postId)
            } catch (exception: Exception) {
                _error.value = exception.message
            }
        }
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

data class PostsListUiState(
    val posts: List<PostWithSubscription> = emptyList(),
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