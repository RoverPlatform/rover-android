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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.rover.sdk.notifications.communicationhub.conversations.ConversationListItem
import io.rover.sdk.notifications.communicationhub.posts.PostEntity
import io.rover.sdk.notifications.communicationhub.posts.PostListItem
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription
import io.rover.sdk.notifications.communicationhub.posts.SubscriptionEntity

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun Messages(
    rows: List<MessageFeedRow>,
    searchQuery: String,
    displayTitle: String,
    isExpanded: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    onPostClick: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )

    Surface(
        modifier = modifier
            .fillMaxSize(),

        color = MaterialTheme.colorScheme.background,

    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SearchBar(
                // this search bar is always being embedded, within a context where a Scaffold
                // and app bar are present, so we'll turn off the clever.
//                windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
                windowInsets = WindowInsets.navigationBars,
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChanged,
                        onSearch = { onExpandedChanged(false) },
                        expanded = isExpanded,
                        onExpandedChange = onExpandedChanged,
                        placeholder = { Text("Search $displayTitle") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        },
                        trailingIcon = {
                            if (isExpanded) {
                                IconButton(
                                    onClick = {
                                        onSearchQueryChanged("")
                                        onExpandedChanged(false)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Close search"
                                    )
                                }
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                    )
                },
                expanded = isExpanded,
                onExpandedChange = onExpandedChanged,
                modifier = Modifier
                    // Search Bar doesn't appear to provide padding on the bottom.
                    .padding(bottom = 16.dp)
            ) {
                // search results:
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        messageFeedRows(rows, onPostClick, onConversationClick)
                    }

                    PullRefreshIndicator(
                        refreshing = isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            // Show message rows when search bar is NOT expanded (collapsed state)
            if (!isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        messageFeedRows(rows, onPostClick, onConversationClick)
                    }

                    PullRefreshIndicator(
                        refreshing = isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

private fun LazyListScope.messageFeedRows(
    rows: List<MessageFeedRow>,
    onPostClick: (String) -> Unit,
    onConversationClick: (String) -> Unit,
) {
    itemsIndexed(
        items = rows,
        key = { _, row -> messageFeedRowKey(row) },
    ) { index, row ->
        Column {
            when (row) {
                is MessageFeedRow.Post -> {
                    PostListItem(
                        postWithSubscription = row.postWithSubscription,
                        onClick = { onPostClick(row.postWithSubscription.post.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is MessageFeedRow.Conversation -> {
                    ConversationListItem(
                        senderName = row.senderName,
                        senderAvatarUrl = row.senderAvatarUrl,
                        subject = row.subject,
                        preview = row.preview,
                        timestamp = row.timestamp,
                        isUnread = row.isUnread,
                        onClick = { onConversationClick(row.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (index < rows.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.testTag(MESSAGE_ROW_DIVIDER_TEST_TAG),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

internal const val MESSAGE_ROW_DIVIDER_TEST_TAG = "message-row-divider"

private fun messageFeedRowKey(row: MessageFeedRow): String = when (row) {
    is MessageFeedRow.Post -> "post:${row.postWithSubscription.post.id}"
    is MessageFeedRow.Conversation -> "conversation:${row.id}"
}

private fun samplePostsData(): List<PostWithSubscription> {
    val currentTime = System.currentTimeMillis()
    val oneHour = 3600000L
    val oneDay = 86400000L

    val teamNewsSubscription = SubscriptionEntity(
        id = "team-news",
        name = "Team News",
        description = "Latest updates about your favorite team",
        optIn = true,
        status = "active"
    )

    val playerStatsSubscription = SubscriptionEntity(
        id = "player-stats",
        name = "Player Statistics",
        description = "Weekly player performance reports",
        optIn = true,
        status = "active"
    )

    val gameUpdatesSubscription = SubscriptionEntity(
        id = "game-updates",
        name = "Game Updates",
        description = "Live scores and game highlights",
        optIn = true,
        status = "active"
    )

    val fantasySubscription = SubscriptionEntity(
        id = "fantasy",
        name = "Fantasy Sports",
        description = "Your fantasy team performance and tips",
        optIn = true,
        status = "active"
    )

    return listOf(
        PostWithSubscription(
            post = PostEntity(
                id = "1",
                subject = "🏀 Lakers vs Warriors Tonight!",
                previewText = "Game starts at 8 PM PT. Don't miss LeBron's return to the court after injury recovery.",
                receivedAt = currentTime - oneHour,
                url = "https://example.com/game/lakers-warriors",
                isRead = false,
                coverImageURL = "https://example.com/images/lakers-warriors.jpg",
                subscriptionId = "game-updates"
            ),
            subscription = gameUpdatesSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "2",
                subject = "⚽ Weekly Team Stats Report",
                previewText = "Your team had an amazing week! Check out the key performance metrics and player highlights.",
                receivedAt = currentTime - 2 * oneDay,
                url = "https://example.com/stats/weekly",
                isRead = true,
                coverImageURL = null,
                subscriptionId = "player-stats"
            ),
            subscription = playerStatsSubscription
        ),
    )
}

private fun sampleRowsData(): List<MessageFeedRow> {
    return listOf(
        MessageFeedRow.Conversation(
            id = "conversation-preview",
            senderName = "Morgan Lee",
            senderAvatarUrl = "https://example.com/avatar.png",
            subject = "Concierge Follow-up",
            preview = "Can we move this to tomorrow?",
            timestamp = System.currentTimeMillis() - 120000L,
            isUnread = true,
        )
    ) + samplePostsData().map { MessageFeedRow.Post(it) }
}

@Preview
@Composable
private fun MessagesPreview() {
    Messages(
        rows = sampleRowsData(),
        searchQuery = "",
        isExpanded = false,
        onSearchQueryChanged = { },
        onExpandedChanged = { },
        onPostClick = { },
        onConversationClick = { },
        onRefresh = { },
        isRefreshing = false,
        displayTitle = "Inbox",
    )
}
