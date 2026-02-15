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

package io.rover.sdk.notifications.communicationhub.data.database

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import io.rover.sdk.notifications.communicationhub.data.dto.SubscriptionItem
import io.rover.sdk.notifications.communicationhub.data.dto.PostItem
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import org.junit.Test
import java.util.*

/**
 * Tests for Engage API's Room database integration and persistence
 */
class RoverEngageDatabaseTest : RoverEngageTestBase() {


    /**
     * Test post create vs update logic with OR logic for isRead field
     */
    @Test
    fun testPostUpsertBehavior() = runTest {
        val postsDao = database!!.postsDao()
        
        // Create initial test post using base class helper
        val postId = createUniquePostId()
        val initialPostItem = createTestPost(
            id = postId,
            subject = "Initial Subject",
            subscriptionID = null, // No subscription for simplicity
            isRead = false
        )

        // Test CREATE: First time creating a post via repository
        repository.upsertPostFromDto(initialPostItem)

        // Verify post was created using base class assertion helper
        val createdPost = postsDao.getPostById(postId)
        assertPostProperties(createdPost, "Initial Subject", expectedIsRead = false)

        // Test UPDATE: Modify existing post and mark as read
        val updatedPostItem = initialPostItem.copy(
            subject = "Updated Subject",
            previewText = "Updated preview text",
            receivedAt = Date(currentTimeMillis() + 3600000),  // 1 hour later
            url = "https://example.com/updated",
            coverImageURL = "https://example.com/updated.jpg",
            isRead = true
        )

        repository.upsertPostFromDto(updatedPostItem)

        // Verify post was updated, not duplicated
        val allPosts = postsDao.getAllPosts().first()
        assert(allPosts.size == 1) { "Should still have only one post (updated, not duplicated)" }

        val retrievedUpdatedPost = postsDao.getPostById(postId)
        assertPostProperties(retrievedUpdatedPost, "Updated Subject", expectedIsRead = true)
        assert(retrievedUpdatedPost?.previewText == "Updated preview text") { "Post preview should be updated" }
        assert(retrievedUpdatedPost?.url == "https://example.com/updated") { "Post URL should be updated" }

        // Test READ STATE OR LOGIC: Try to set back to false via repository
        val revertReadPostItem = updatedPostItem.copy(
            subject = "Final Subject",
            previewText = "Final preview text",
            receivedAt = Date(currentTimeMillis() + 7200000),
            url = "https://example.com/final",
            coverImageURL = null,
            isRead = false  // Try to set back to unread
        )

        // Test that repository handles OR logic automatically
        repository.upsertPostFromDto(revertReadPostItem)

        val finalPost = postsDao.getPostById(postId)
        assertPostProperties(finalPost, "Final Subject", expectedIsRead = true)
        // Verify OR logic: Post should remain read due to (existing.isRead || new.isRead)
    }

    /**
     * Test foreign key relationships between posts and subscriptions
     */
    @Test
    fun testSubscriptionRelationshipCreation() = runTest {
        val subscriptionsDao = database!!.subscriptionsDao()
        val postsDao = database!!.postsDao()

        // Create test subscription data
        val subscriptionItem = PostItem(
            id = "test-post-${UUID.randomUUID()}",
            subject = "Test Subject",
            previewText = "Test preview",
            receivedAt = Date(System.currentTimeMillis()),
            url = "https://example.com/test",
            coverImageURL = null,
            subscriptionID = "sub-123",
            isRead = false
        )

        // Create subscription first using repository
        repository.upsertPostFromDto(subscriptionItem)

        // Verify subscription was created (placeholder should be created by repository)
        val createdSubscription = subscriptionsDao.getSubscriptionById("sub-123")
        assert(createdSubscription != null) { "Subscription should be created" }
        assert(createdSubscription?.name == "") { "Placeholder subscription should have empty name" }
        assert(createdSubscription?.status == "published") { "Subscription status should match" }

        // Create post with subscription relationship
        val postItem = PostItem(
            id = "post-${UUID.randomUUID()}",
            subject = "Test Post",
            previewText = "Post with subscription relationship",
            receivedAt = Date(System.currentTimeMillis()),
            url = "https://example.com/post",
            coverImageURL = null,
            subscriptionID = "sub-123", // Link to existing subscription
            isRead = false
        )

        // Create post via repository (should use existing subscription)
        repository.upsertPostFromDto(postItem)

        // Verify relationship was established
        val createdPost = postsDao.getPostById(postItem.id)
        assert(createdPost != null) { "Post should be created" }
        assert(createdPost?.subscriptionId == "sub-123") { "Post should link to correct subscription" }

        // Test relationship update when post subscription changes
        val updatedPostItem = postItem.copy(
            subject = "Updated Post",
            previewText = "Post with updated subscription",
            subscriptionID = "sub-456" // Different subscription (doesn't exist yet)
        )

        repository.upsertPostFromDto(updatedPostItem)

        // Verify relationship was updated and placeholder subscription created
        val updatedPost = postsDao.getPostById(postItem.id)
        assert(updatedPost != null) { "Post should still exist" }
        assert(updatedPost?.subscriptionId == "sub-456") { "Post should link to new subscription" }

        // Verify placeholder subscription was created
        val placeholderSubscription = subscriptionsDao.getSubscriptionById("sub-456")
        assert(placeholderSubscription != null) { "Placeholder subscription should be created" }
        assert(placeholderSubscription?.name == "") { "Placeholder subscription should have empty name" }
        assert(placeholderSubscription?.status == "published") { "Placeholder subscription should have default status" }
        assert(placeholderSubscription?.optIn == false) { "Placeholder subscription should have default optIn" }

        // Verify we have both subscriptions in database
        val allSubscriptions = subscriptionsDao.getAllSubscriptions()
        assert(allSubscriptions.size == 2) { "Should have 2 subscriptions (original placeholder + new placeholder)" }
    }

    /**
     * Test cursor state management and persistence
     */
    @Test
    fun testCursorPersistenceAndRetrieval() = runTest {
        val cursorsDao = database!!.cursorsDao()

        // Test initial state - no cursor should exist
        val initialCursor = cursorsDao.getCursor("posts")
        assert(initialCursor == null) { "Initial cursor should be null" }

        // Test creating first cursor
        val firstCursor = "cursor-page-1"
        cursorsDao.updateCursor("posts", firstCursor)

        // Verify cursor was saved
        val retrievedFirstCursor = cursorsDao.getCursor("posts")
        assert(retrievedFirstCursor == firstCursor) { "Should retrieve the same cursor that was saved" }

        // Test updating existing cursor
        val secondCursor = "cursor-page-2"
        cursorsDao.updateCursor("posts", secondCursor)

        // Verify cursor was updated, not duplicated
        val retrievedSecondCursor = cursorsDao.getCursor("posts")
        assert(retrievedSecondCursor == secondCursor) { "Should retrieve the updated cursor" }

        // Verify only one cursor entity exists
        val allCursors = cursorsDao.getAllCursors()
        assert(allCursors.size == 1) { "Should have exactly one cursor entity" }
        assert(allCursors.first().cursor == secondCursor) { "The single cursor should have the latest value" }
        assert(allCursors.first().roverEntity == "posts") { "Cursor should be tagged for posts entity" }

        // Test setting cursor to null (clearing it)
        cursorsDao.updateCursor("posts", null)

        val nullCursor = cursorsDao.getCursor("posts")
        assert(nullCursor == null) { "Cursor should be null after clearing" }

        // Verify cursor entity still exists but with null value
        val clearedCursors = cursorsDao.getAllCursors()
        assert(clearedCursors.size == 1) { "Cursor entity should still exist" }
        assert(clearedCursors.first().cursor == null) { "Cursor value should be null" }

        // Test setting cursor again after clearing
        val thirdCursor = "cursor-page-3"
        cursorsDao.updateCursor("posts", thirdCursor)

        val retrievedThirdCursor = cursorsDao.getCursor("posts")
        assert(retrievedThirdCursor == thirdCursor) { "Should be able to set cursor again after clearing" }

        // Test persistence - the in-memory database automatically persists
        // Verify final state
        val finalCursor = cursorsDao.getCursor("posts")
        assert(finalCursor == thirdCursor) { "Cursor should persist" }

        // Test multiple entity types
        cursorsDao.updateCursor("articles", "article-cursor-1")
        val articleCursor = cursorsDao.getCursor("articles")
        assert(articleCursor == "article-cursor-1") { "Should handle multiple entity types" }

        // Verify we now have two cursor entities
        val allFinalCursors = cursorsDao.getAllCursors()
        assert(allFinalCursors.size == 2) { "Should have two cursor entities for different entity types" }
    }

    /**
     * Test Core Data transaction ACID properties (Android equivalent)
     */
    @Test
    fun testDatabaseTransactionIntegrity() = runTest {
        val subscriptionsDao = database!!.subscriptionsDao()
        val postsDao = database!!.postsDao()

        // Test ATOMICITY: All operations in a transaction succeed or all fail
        val subscriptionItems = listOf(
            SubscriptionItem(
                id = "sub-1",
                name = "Sub 1", 
                description = "First",
                optIn = true,
                status = "published"
            ),
            SubscriptionItem(
                id = "sub-2",
                name = "Sub 2",
                description = "Second", 
                optIn = false,
                status = "archived"
            ),
            SubscriptionItem(
                id = "sub-3",
                name = "Sub 3",
                description = "Third",
                optIn = true,
                status = "unpublished"
            )
        )

        // This should succeed atomically - create subscriptions first
        subscriptionItems.forEach { subscriptionItem ->
            subscriptionsDao.upsertSubscription(subscriptionItem.toEntity())
        }

        // Verify all subscriptions were created (atomicity)
        val allSubscriptions = subscriptionsDao.getAllSubscriptions()
        assert(allSubscriptions.size == 3) { "All subscriptions should be created atomically, but found ${allSubscriptions.size}" }

        // Test CONSISTENCY: Data constraints are maintained
        val postItem = PostItem(
            id = "test-post-${UUID.randomUUID()}",
            subject = "Test Post",
            previewText = "Testing consistency",
            receivedAt = Date(),
            url = "https://example.com",
            coverImageURL = null,
            subscriptionID = "sub-1", // Valid subscription ID
            isRead = false
        )

        repository.upsertPostFromDto(postItem)

        // Verify relationship consistency
        val createdPost = postsDao.getPostById(postItem.id)
        assert(createdPost != null) { "Post should be created" }
        assert(createdPost?.subscriptionId == "sub-1") { "Post should have valid subscription relationship" }

        // Verify subscription relationship
        val subscription = subscriptionsDao.getSubscriptionById("sub-1")
        assert(subscription != null) { "Subscription should exist for consistency" }
        assert(createdPost?.subscriptionId == subscription?.id) { "Relationship should be consistent" }

        // Test ISOLATION: Concurrent operations don't interfere
        // Create multiple posts concurrently
        val concurrentPostItems = (1..5).map { index ->
            PostItem(
                id = "concurrent-post-$index-${UUID.randomUUID()}",
                subject = "Concurrent Post $index",
                previewText = "Testing isolation $index",
                receivedAt = Date(System.currentTimeMillis() + index * 1000),
                url = "https://example.com/$index",
                coverImageURL = null,
                subscriptionID = "sub-${((index - 1) % 3) + 1}", // Rotate through sub-1, sub-2, sub-3
                isRead = false
            )
        }

        // Execute concurrent post creations
        val deferredResults = concurrentPostItems.map { postItem ->
            async { repository.upsertPostFromDto(postItem) }
        }

        // Wait for all to complete
        deferredResults.forEach { deferred -> deferred.await() }

        // All should succeed (isolation maintained)
        val allPosts = postsDao.getAllPosts().first()
        assert(allPosts.size == 6) { "Should have original post + 5 concurrent posts, but found ${allPosts.size}" }

        // Test DURABILITY: Changes persist after save
        val preCommitPostCount = allPosts.size

        // Force save (Room handles this automatically, but verify data persists)
        val postCommitPosts = postsDao.getAllPosts().first()
        assert(postCommitPosts.size == preCommitPostCount) { "Post count should persist after save" }

        // Test transaction rollback scenario
        // Create a post with a nonexistent subscription that should create a placeholder
        val invalidPost = PostItem(
            id = "invalid-post-${UUID.randomUUID()}",
            subject = "",  // Empty subject might be invalid depending on constraints
            previewText = "Testing rollback",
            receivedAt = Date(),
            url = "https://example.com/invalid",
            coverImageURL = null,
            subscriptionID = "nonexistent-sub", // This will create a placeholder, so it's actually valid
            isRead = false
        )

        // This should still succeed because the implementation creates placeholder subscriptions
        repository.upsertPostFromDto(invalidPost)

        // Verify placeholder subscription was created (consistency maintained)
        val placeholderSub = subscriptionsDao.getSubscriptionById("nonexistent-sub")
        assert(placeholderSub != null) { "Placeholder subscription should be created to maintain consistency" }

        // Test data integrity after multiple operations
        val finalPosts = postsDao.getAllPosts().first()
        val finalSubscriptions = subscriptionsDao.getAllSubscriptions()

        assert(finalPosts.size == 7) { "Should have 7 posts total" }
        assert(finalSubscriptions.size == 4) { "Should have 4 subscriptions (3 original + 1 placeholder)" }

        // Verify all posts have valid subscription relationships
        finalPosts.forEach { post ->
            assert(post.subscriptionId != null) { "Every post should have a subscription relationship" }
            val relatedSubscription = subscriptionsDao.getSubscriptionById(post.subscriptionId!!)
            assert(relatedSubscription != null) { "Every subscription should have an ID" }
        }
    }

    /**
     * Test placeholder subscription creation for orphaned posts
     */
    @Test
    fun testPlaceholderSubscriptionCreation() = runTest {
        val subscriptionsDao = database!!.subscriptionsDao()
        val postsDao = database!!.postsDao()

        // Test creating post with nonexistent subscription ID
        val orphanedPostItem = PostItem(
            id = "orphaned-post-${UUID.randomUUID()}",
            subject = "Orphaned Post",
            previewText = "Post without existing subscription",
            receivedAt = Date(),
            url = "https://example.com/orphaned",
            coverImageURL = null,
            subscriptionID = "nonexistent-subscription-id",
            isRead = false
        )

        // Create the orphaned post
        repository.upsertPostFromDto(orphanedPostItem)

        // Verify post was created
        val createdPost = postsDao.getPostById(orphanedPostItem.id)
        assert(createdPost != null) { "Orphaned post should be created" }
        assert(createdPost?.subject == "Orphaned Post") { "Post properties should be correct" }

        // Verify placeholder subscription was automatically created
        val placeholderSub = subscriptionsDao.getSubscriptionById("nonexistent-subscription-id")
        assert(placeholderSub != null) { "Placeholder subscription should be automatically created" }

        // Verify placeholder subscription has default values
        assert(placeholderSub?.id == "nonexistent-subscription-id") { "Placeholder should have correct ID" }
        assert(placeholderSub?.name == "") { "Placeholder should have empty name" }
        assert(placeholderSub?.description == "") { "Placeholder should have empty description" }
        assert(placeholderSub?.optIn == false) { "Placeholder should have default optIn = false" }
        assert(placeholderSub?.status == "published") { "Placeholder should have default status = published" }

        // Verify relationship is established
        assert(createdPost?.subscriptionId == "nonexistent-subscription-id") { "Post should link to placeholder subscription" }

        // Test multiple orphaned posts with same nonexistent subscription
        val secondOrphanedPost = PostItem(
            id = "second-orphaned-post-${UUID.randomUUID()}",
            subject = "Second Orphaned Post",
            previewText = "Another post with same nonexistent subscription",
            receivedAt = Date(System.currentTimeMillis() + 3600000), // 1 hour later
            url = "https://example.com/orphaned2",
            coverImageURL = null,
            subscriptionID = "nonexistent-subscription-id", // Same nonexistent ID
            isRead = true
        )

        repository.upsertPostFromDto(secondOrphanedPost)

        // Verify no duplicate placeholder subscription was created
        val allSubscriptions = subscriptionsDao.getAllSubscriptions()
        val placeholderSubscriptions = allSubscriptions.filter { it.id == "nonexistent-subscription-id" }
        assert(placeholderSubscriptions.size == 1) { "Should have only one placeholder subscription" }

        // Verify both posts link to the same placeholder
        val allPosts = postsDao.getAllPosts().first()
        val postsWithPlaceholder = allPosts.filter { it.subscriptionId == "nonexistent-subscription-id" }
        assert(postsWithPlaceholder.size == 2) { "Placeholder subscription should now have two posts" }

        // Test placeholder gets replaced when real subscription data arrives
        val realSubscriptionItem = SubscriptionItem(
            id = "nonexistent-subscription-id", // Same ID as placeholder
            name = "Real Subscription Name",
            description = "Real subscription description",
            optIn = false,
            status = "archived"
        )

        subscriptionsDao.upsertSubscription(realSubscriptionItem.toEntity())

        // Verify placeholder was updated with real data
        val updatedSubscription = subscriptionsDao.getSubscriptionById("nonexistent-subscription-id")
        assert(updatedSubscription != null) { "Subscription should still exist" }
        assert(updatedSubscription?.name == "Real Subscription Name") { "Placeholder should be updated with real name" }
        assert(updatedSubscription?.description == "Real subscription description") { "Description should be updated" }
        assert(updatedSubscription?.optIn == false) { "OptIn should be updated" }
        assert(updatedSubscription?.status == "archived") { "Status should be updated" }

        // Verify posts still link to the updated subscription
        val updatedPosts = postsDao.getAllPosts().first()
        val postsWithUpdatedSub = updatedPosts.filter { it.subscriptionId == "nonexistent-subscription-id" }
        assert(postsWithUpdatedSub.size == 2) { "Updated subscription should still have both posts" }

        val firstPost = postsDao.getPostById(orphanedPostItem.id)
        val secondPost = postsDao.getPostById(secondOrphanedPost.id)

        assert(firstPost?.subscriptionId == "nonexistent-subscription-id") { "First post should link to updated subscription" }
        assert(secondPost?.subscriptionId == "nonexistent-subscription-id") { "Second post should link to updated subscription" }

        // Test orphaned post with null subscription ID
        val nilSubscriptionPost = PostItem(
            id = "nil-sub-post-${UUID.randomUUID()}",
            subject = "Post with nil subscription",
            previewText = "Post without any subscription",
            receivedAt = Date(),
            url = "https://example.com/nil-sub",
            coverImageURL = null,
            subscriptionID = null, // No subscription
            isRead = false
        )

        repository.upsertPostFromDto(nilSubscriptionPost)

        val nilSubPost = postsDao.getPostById(nilSubscriptionPost.id)
        assert(nilSubPost != null) { "Should successfully create post with nil subscription" }
        assert(nilSubPost?.subscriptionId == null) { "Post with nil subscriptionID should have nil subscription relationship" }
    }
}
