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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.rover.sdk.notifications.communicationhub.messages.MessageAvatarShape
import io.rover.sdk.notifications.communicationhub.messages.MessageListItemRow

@Composable
fun PostListItem(
    postWithSubscription: PostWithSubscription,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val post = postWithSubscription.post
    val subscription = postWithSubscription.subscription

    MessageListItemRow(
        isRead = post.isRead,
        senderName = subscription?.name,
        subject = post.subject,
        preview = post.previewText,
        timestamp = post.receivedAt,
        avatarUrl = subscription?.logoURL,
        avatarShape = MessageAvatarShape.Subscription,
        onClick = onClick,
        modifier = modifier,
    )
}

private fun sampleUnreadPost(): PostWithSubscription {
    val subscription = SubscriptionEntity(
        id = "breaking-news",
        name = "Breaking News",
        description = "Latest breaking sports news",
        optIn = true,
        status = "active"
    )

    val post = PostEntity(
        id = "unread-1",
        subject = "🏀 Breaking: Star Player Traded!",
        previewText = "In a shocking move, the team's MVP has been traded to their biggest rival. Full details and analysis inside.",
        receivedAt = System.currentTimeMillis() - 3600000L, // 1 hour ago
        url = "https://example.com/breaking-trade",
        isRead = false,
        coverImageURL = "https://example.com/images/trade-news.jpg",
        subscriptionId = "breaking-news"
    )

    return PostWithSubscription(post = post, subscription = subscription)
}

private fun sampleReadPost(): PostWithSubscription {
    val subscription = SubscriptionEntity(
        id = "weekly-reports",
        name = "Weekly Reports",
        description = "Weekly team and player performance reports",
        optIn = true,
        status = "active"
    )

    val post = PostEntity(
        id = "read-1",
        subject = "📊 Weekly Performance Report",
        previewText = "Your team's performance summary for this week. See how your favorite players are doing this season.",
        receivedAt = System.currentTimeMillis() - 86400000L * 2, // 2 days ago
        url = "https://example.com/weekly-report",
        isRead = true,
        coverImageURL = null,
        subscriptionId = "weekly-reports"
    )

    return PostWithSubscription(post = post, subscription = subscription)
}

private fun samplePostWithoutSubscription(): PostWithSubscription {
    val post = PostEntity(
        id = "no-sub-1",
        subject = "🎯 Fantasy League Update",
        previewText = "Your fantasy league standings have been updated. Check your ranking and upcoming matchups.",
        receivedAt = System.currentTimeMillis() - 86400000L, // 1 day ago
        url = "https://example.com/fantasy-update",
        isRead = false,
        coverImageURL = "https://example.com/images/fantasy.jpg",
        subscriptionId = null
    )

    return PostWithSubscription(post = post, subscription = null)
}

private fun sampleLongContentPost(): PostWithSubscription {
    val subscription = SubscriptionEntity(
        id = "game-recaps",
        name = "Game Recaps & Analysis",
        description = "Detailed game recaps and expert analysis",
        optIn = true,
        status = "active"
    )

    val post = PostEntity(
        id = "long-1",
        subject = "🏈 Championship Game Recap: An Epic Battle for the Ages That Will Be Remembered Forever",
        previewText = "What an incredible game! This championship matchup had everything - overtime drama, clutch plays, record-breaking performances, and moments that will be talked about for years to come. Our comprehensive recap covers every major play, statistical breakdown, and expert analysis of what made this game so special.",
        receivedAt = System.currentTimeMillis() - 43200000L, // 12 hours ago
        url = "https://example.com/championship-recap",
        isRead = true,
        coverImageURL = "https://example.com/images/championship.jpg",
        subscriptionId = "game-recaps"
    )

    return PostWithSubscription(post = post, subscription = subscription)
}

@Preview
@Composable
private fun PostListItemUnreadPreview() {
    PostListItem(
        postWithSubscription = sampleUnreadPost(),
        onClick = { }
    )
}

@Preview
@Composable
private fun PostListItemReadPreview() {
    PostListItem(
        postWithSubscription = sampleReadPost(),
        onClick = { }
    )
}

@Preview
@Composable
private fun PostListItemNoCoverImagePreview() {
    PostListItem(
        postWithSubscription = sampleReadPost(),
        onClick = { }
    )
}

@Preview
@Composable
private fun PostListItemNoSubscriptionPreview() {
    PostListItem(
        postWithSubscription = samplePostWithoutSubscription(),
        onClick = { }
    )
}

@Preview
@Composable
private fun PostListItemLongContentPreview() {
    PostListItem(
        postWithSubscription = sampleLongContentPost(),
        onClick = { }
    )
}
