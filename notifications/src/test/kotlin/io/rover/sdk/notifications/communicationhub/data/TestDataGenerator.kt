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

package io.rover.sdk.notifications.communicationhub.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import io.rover.sdk.notifications.communicationhub.data.dto.PostItem
import io.rover.sdk.notifications.communicationhub.data.dto.PostsSyncResponse
import io.rover.sdk.notifications.communicationhub.data.dto.SubscriptionItem
import io.rover.sdk.notifications.communicationhub.data.dto.SubscriptionsSyncResponse
import java.util.Date
import kotlin.random.Random

/**
 * Test data generator for creating predictable test data.
 * Uses Moshi for JSON generation to ensure consistency with actual DTOs.
 */
object TestDataGenerator {

    private const val BASE_URL = "https://example.com"

    private val moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()

    // MARK: - Object Generation

    fun createTestSubscriptions(count: Int) = (0..<count).map { index ->
        SubscriptionItem(
            id = "subscription-$index",
            name = "Test Subscription $index",
            description = "Description for Test Subscription $index",
            optIn = index % 2 == 0,
            status = if (index % 3 == 0) "published" else "unpublished"
        )
    }

    fun createTestPosts(count: Int, subscriptionID: String? = null) = (0..<count).map { index ->
        createTestPost(
            id = "test-post-$index",
            subject = "Test Post $index",
            subscriptionID = subscriptionID ?: "subscription-${index % 3}"
        )
    }

    fun createTestPost(
        id: String = "test-post",
        subject: String = "Test Post",
        subscriptionID: String? = "test-subscription"
    ) = PostItem(
        id = id,
        subject = subject,
        previewText = "This is the preview text for $subject",
        receivedAt = Date(System.currentTimeMillis() - Random.nextLong(0, 86400000)),
        url = "$BASE_URL/post/$id",
        coverImageURL = "$BASE_URL/images/$id.jpg",
        subscriptionID = subscriptionID,
        isRead = false
    )

    // MARK: - JSON Generation (using Moshi for consistency)

    fun createTestSubscriptionsJson(count: Int): String =
        moshi.adapter(SubscriptionsSyncResponse::class.java)
            .toJson(SubscriptionsSyncResponse(createTestSubscriptions(count)))

    fun createTestPostsJson(count: Int, subscriptionId: String? = null): String =
        moshi.adapter(PostsSyncResponse::class.java)
            .toJson(PostsSyncResponse(
                posts = createTestPosts(count, subscriptionId),
                nextCursor = null,
                hasMore = false
            ))

    fun createTestPostJson(
        id: String = "test-post",
        subject: String = "Test Post",
        subscriptionId: String = "test-subscription"
    ): String = moshi.adapter(PostsSyncResponse::class.java)
        .toJson(PostsSyncResponse(
            posts = listOf(createTestPost(id, subject, subscriptionId)),
            nextCursor = null,
            hasMore = false
        ))

    // MARK: - Pagination Test Data

    fun createPaginatedPostResponses(
        totalPosts: Int,
        pageSize: Int,
        subscriptionId: String = "subscription-0"
    ): List<String> = (0 until (totalPosts + pageSize - 1) / pageSize).map { pageIndex ->
        val startIndex = pageIndex * pageSize
        val endIndex = minOf(startIndex + pageSize, totalPosts)
        val hasMore = endIndex < totalPosts
        val nextCursor = if (hasMore) "cursor-page-${pageIndex + 1}" else null

        moshi.adapter(PostsSyncResponse::class.java).toJson(
            PostsSyncResponse(
                posts = (startIndex..<endIndex).map { index ->
                    createTestPost("test-post-$index", "Test Post $index", subscriptionId)
                },
                nextCursor = nextCursor,
                hasMore = hasMore
            )
        )
    }

    // MARK: - Object Serialization (for actual objects)

    fun serializeSubscriptionsToJson(subscriptions: List<SubscriptionItem>): String =
        moshi.adapter(SubscriptionsSyncResponse::class.java)
            .toJson(SubscriptionsSyncResponse(subscriptions))

    fun serializePostsToJson(posts: List<PostItem>): String =
        moshi.adapter(PostsSyncResponse::class.java)
            .toJson(PostsSyncResponse(
                posts = posts,
                nextCursor = null,
                hasMore = false
            ))

    // MARK: - Empty Responses

    fun emptySubscriptionsJson() = createTestSubscriptionsJson(0)
    fun emptyPostsJson() = createTestPostsJson(0)
}
