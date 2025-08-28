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

package io.rover.sdk.notifications.communicationhub.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rover.sdk.core.Rover
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.notifications.communicationHubRepository
import io.rover.sdk.notifications.communicationhub.openLink
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostWithSubscription
import io.rover.sdk.notifications.ui.screens.PostDetail
import io.rover.sdk.notifications.ui.screens.PostsList
import io.rover.sdk.notifications.ui.viewmodels.PostsListViewModel

/**
 * Embed this view within a tab to integrate the Rover Communication Hub.
 *
 * @param modifier Modifier for styling
 * @param title Optional title for the Communication Hub (defaults to "Inbox")
 * @param sourceColor The source color to use for automatic generating the theme.
 *
 * Will adopt an automatically generated Material 3 theme based on the provided accent color.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
public fun CommunicationHub(
    modifier: Modifier = Modifier,
    title: String? = null,
    sourceColor: Color,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val colorScheme = automaticMaterialScheme(sourceColor = sourceColor, isDark = isDark, dynamicColor = false)

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        CommunicationHub(modifier = modifier, title = title)
    }
}

/**
 * Embed this view within a tab to integrate the Rover Communication Hub.
 *
 * @param modifier Modifier for styling
 * @param title Optional title for the Communication Hub (defaults to "Inbox")
 *
 * Will adopt your Material 3 theme.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
public fun CommunicationHub(
    modifier: Modifier = Modifier,
    title: String? = null,
    initialPostID: String? = null,
) {
    val context = LocalContext.current
    val rover = Rover.shared

    val postsRepository = rover.communicationHubRepository
    val linkOpen = rover.resolve(LinkOpenInterface::class.java)

    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<PostWithSubscription>()
    val coroutineScope = rememberCoroutineScope()
    val displayTitle = title ?: "Inbox"



    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavigableListDetailPaneScaffold(
            navigator = scaffoldNavigator,
            listPane = {
                AnimatedPane {
                    PostsListComponent(
                        displayTitle = displayTitle,
                        postsRepository = postsRepository,
                        initialPostID = initialPostID,
                        scaffoldNavigator = scaffoldNavigator
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    scaffoldNavigator.currentDestination?.contentKey?.let { postWithSubscription ->
                        PostDetail(
                            post = postWithSubscription,
                            postsRepository = postsRepository,
                            onBackClick = {
                                coroutineScope.launch {
                                    scaffoldNavigator.navigateBack()
                                }
                            },
                            onOpenUrl = { url ->
                                linkOpen?.openLink(url, context)
                            }
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun PostsListComponent(
    displayTitle: String,
    postsRepository: io.rover.sdk.notifications.communicationhub.data.repository.CommHubRepository,
    initialPostID: String?,
    scaffoldNavigator: ThreePaneScaffoldNavigator<PostWithSubscription>,
    modifier: Modifier = Modifier
) {
    val viewModel: PostsListViewModel = viewModel {
        PostsListViewModel(postsRepository)
    }
    val coroutineScope = rememberCoroutineScope()
    
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle initial post navigation when posts are loaded
    LaunchedEffect(uiState.posts, initialPostID) {
        if (initialPostID != null && uiState.posts.isNotEmpty()) {
            val targetPost = uiState.posts.find { it.post.id == initialPostID }
            targetPost?.let { post ->
                scaffoldNavigator.navigateTo(
                    ListDetailPaneScaffoldRole.Detail,
                    post
                )
            }
        }
    }

    PostsList(
        posts = uiState.posts,
        searchQuery = uiState.searchQuery,
        isExpanded = uiState.isExpanded,
        displayTitle = displayTitle,
        onSearchQueryChanged = { query -> viewModel.updateSearchQuery(query) },
        onExpandedChanged = { expanded -> viewModel.updateExpanded(expanded) },
        onPostClick = { postId ->
            viewModel.markPostAsRead(postId)
            val targetPost = uiState.posts.find { it.post.id == postId }
            targetPost?.let { post ->
                coroutineScope.launch {
                    scaffoldNavigator.navigateTo(
                        ListDetailPaneScaffoldRole.Detail,
                        post
                    )
                }
            }
        },
        onRefresh = { viewModel.refresh() },
        isRefreshing = uiState.isRefreshing,
        modifier = modifier
    )
}
