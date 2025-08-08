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

package io.rover.sdk.notifications.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostEntity
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostWithSubscription
import io.rover.sdk.notifications.communicationhub.data.database.entities.SubscriptionEntity
import io.rover.sdk.notifications.ui.components.PostListItem

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PostsList(
    posts: List<PostWithSubscription>,
    searchQuery: String,
    displayTitle: String,
    isExpanded: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    onPostClick: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,

    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SearchBar(
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(posts) { postWithSubscription ->
                            PostListItem(
                                postWithSubscription = postWithSubscription,
                                onClick = { onPostClick(postWithSubscription.post.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    PullRefreshIndicator(
                        refreshing = isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
            
            // Show posts when search bar is NOT expanded (collapsed state)
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(posts) { postWithSubscription ->
                            PostListItem(
                                postWithSubscription = postWithSubscription,
                                onClick = { onPostClick(postWithSubscription.post.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
        PostWithSubscription(
            post = PostEntity(
                id = "3",
                subject = "🏈 Trade Deadline Updates",
                previewText = "Breaking: Major trades happening now! See which players are moving and how it affects your team.",
                receivedAt = currentTime - 3 * oneHour,
                url = "https://example.com/trades/deadline",
                isRead = false,
                coverImageURL = "https://example.com/images/trade-deadline.jpg",
                subscriptionId = "team-news"
            ),
            subscription = teamNewsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "4",
                subject = "🎯 Your Fantasy Team Performance",
                previewText = "Week 12 recap: You're in 3rd place! Here's how to improve your lineup for next week.",
                receivedAt = currentTime - oneDay,
                url = "https://example.com/fantasy/week12",
                isRead = true,
                coverImageURL = null,
                subscriptionId = "fantasy"
            ),
            subscription = fantasySubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "5",
                subject = "🏆 Championship Finals Preview",
                previewText = "The stage is set! Everything you need to know about the upcoming championship game.",
                receivedAt = currentTime - 6 * oneHour,
                url = "https://example.com/championship/preview",
                isRead = false,
                coverImageURL = "https://example.com/images/championship.jpg",
                subscriptionId = "team-news"
            ),
            subscription = teamNewsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "6",
                subject = "📊 Player of the Week: Sarah Johnson",
                previewText = "Incredible performance with 28 points and 12 assists. See her full stats and highlights.",
                receivedAt = currentTime - 2 * oneDay - oneHour,
                url = "https://example.com/player/sarah-johnson",
                isRead = true,
                coverImageURL = "https://example.com/images/sarah-johnson.jpg",
                subscriptionId = "player-stats"
            ),
            subscription = playerStatsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "7",
                subject = "🚨 Injury Report Update",
                previewText = "Key players on injury list for this week's games. Plan your fantasy lineup accordingly.",
                receivedAt = currentTime - 4 * oneHour,
                url = "https://example.com/injuries/update",
                isRead = false,
                coverImageURL = null,
                subscriptionId = "team-news"
            ),
            subscription = teamNewsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "8",
                subject = "🏅 Season Highlights Reel",
                previewText = "Relive the best moments from this incredible season. Watch the top 10 plays now!",
                receivedAt = currentTime - 3 * oneDay,
                url = "https://example.com/highlights/season",
                isRead = true,
                coverImageURL = "https://example.com/images/highlights.jpg",
                subscriptionId = "game-updates"
            ),
            subscription = gameUpdatesSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "9",
                subject = "📈 Fantasy Draft Tips for Next Season",
                previewText = "Get ahead of the competition! Expert advice on who to target in your upcoming draft.",
                receivedAt = currentTime - 5 * oneDay,
                url = "https://example.com/fantasy/draft-tips",
                isRead = false,
                coverImageURL = null,
                subscriptionId = "fantasy"
            ),
            subscription = fantasySubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "10",
                subject = "🎪 Behind the Scenes: Training Camp",
                previewText = "Exclusive access to training camp footage. See how your favorite players prepare for the season.",
                receivedAt = currentTime - oneDay - 2 * oneHour,
                url = "https://example.com/training-camp",
                isRead = true,
                coverImageURL = "https://example.com/images/training-camp.jpg",
                subscriptionId = "team-news"
            ),
            subscription = teamNewsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "11",
                subject = "⚡ Live Score Updates Available",
                previewText = "Enable notifications for real-time score updates during tonight's triple-header games.",
                receivedAt = currentTime - 30 * 60000L,
                url = "https://example.com/live-scores",
                isRead = false,
                coverImageURL = null,
                subscriptionId = "game-updates"
            ),
            subscription = gameUpdatesSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "12",
                subject = "🏀 Rookie Spotlight: Marcus Williams",
                previewText = "This first-year player is making waves! Check out his incredible debut season stats.",
                receivedAt = currentTime - 4 * oneDay,
                url = "https://example.com/rookie/marcus-williams",
                isRead = true,
                coverImageURL = "https://example.com/images/marcus-williams.jpg",
                subscriptionId = "player-stats"
            ),
            subscription = playerStatsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "13",
                subject = "🎯 Fantasy Waiver Wire Pickups",
                previewText = "Don't miss these hidden gems! Five players you should consider adding to your roster.",
                receivedAt = currentTime - 8 * oneHour,
                url = "https://example.com/fantasy/waiver-wire",
                isRead = false,
                coverImageURL = null,
                subscriptionId = "fantasy"
            ),
            subscription = fantasySubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "14",
                subject = "🏆 Playoff Bracket Predictions",
                previewText = "Our experts weigh in on playoff matchups. See who they think will make it to the finals.",
                receivedAt = currentTime - 6 * oneDay,
                url = "https://example.com/playoffs/predictions",
                isRead = true,
                coverImageURL = "https://example.com/images/playoff-bracket.jpg",
                subscriptionId = "team-news"
            ),
            subscription = teamNewsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "15",
                subject = "📺 Watch Party Events Near You",
                previewText = "Join fellow fans at local sports bars and venues for the big game. Find events in your area.",
                receivedAt = currentTime - 2 * oneHour,
                url = "https://example.com/watch-parties",
                isRead = false,
                coverImageURL = null,
                subscriptionId = "game-updates"
            ),
            subscription = gameUpdatesSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "16",
                subject = "🏅 Coach of the Year Award",
                previewText = "Congratulations to Coach Martinez on this well-deserved recognition for an outstanding season.",
                receivedAt = currentTime - 7 * oneDay,
                url = "https://example.com/coach/award",
                isRead = true,
                coverImageURL = "https://example.com/images/coach-award.jpg",
                subscriptionId = "team-news"
            ),
            subscription = teamNewsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "17",
                subject = "🎮 New Fantasy Game Mode Released",
                previewText = "Try the new daily fantasy challenges! Compete with friends and win exclusive prizes.",
                receivedAt = currentTime - 5 * oneHour,
                url = "https://example.com/fantasy/new-mode",
                isRead = false,
                coverImageURL = "https://example.com/images/fantasy-game.jpg",
                subscriptionId = "fantasy"
            ),
            subscription = fantasySubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "18",
                subject = "📊 Advanced Stats Dashboard Launch",
                previewText = "Dive deeper into player analytics with our new advanced statistics dashboard and insights.",
                receivedAt = currentTime - 3 * oneDay - oneHour,
                url = "https://example.com/stats/dashboard",
                isRead = true,
                coverImageURL = null,
                subscriptionId = "player-stats"
            ),
            subscription = playerStatsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "19",
                subject = "🎊 Season Ticket Holder Perks",
                previewText = "Exclusive benefits for our loyal fans! Check out your special access and rewards program.",
                receivedAt = currentTime - 9 * oneDay,
                url = "https://example.com/season-tickets/perks",
                isRead = false,
                coverImageURL = "https://example.com/images/season-tickets.jpg",
                subscriptionId = "team-news"
            ),
            subscription = teamNewsSubscription
        ),
        PostWithSubscription(
            post = PostEntity(
                id = "20",
                subject = "🏟️ Stadium Experience Survey",
                previewText = "Help us improve! Share your feedback about your recent stadium visit and concession experience.",
                receivedAt = currentTime - 10 * oneDay,
                url = "https://example.com/survey/stadium",
                isRead = true,
                coverImageURL = null,
                subscriptionId = "game-updates"
            ),
            subscription = gameUpdatesSubscription
        )
    )
}

@Preview
@Composable
private fun PostsListPreview() {
    PostsList(
        posts = samplePostsData(),
        searchQuery = "",
        isExpanded = false,
        onSearchQueryChanged = { },
        onExpandedChanged = { },
        onPostClick = { },
        onRefresh = { },
        isRefreshing = false,
        displayTitle = "Inbox",
    )
}