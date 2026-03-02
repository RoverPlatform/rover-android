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

package io.rover.sdk.notifications.communicationhub.data.repository

import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import io.rover.sdk.notifications.communicationhub.data.TestDataGenerator
import io.rover.sdk.notifications.communicationhub.data.dto.PostItem
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response
import java.io.IOException

/**
 * Tests for the two-phase sync functionality in CommHubRepository
 * 
 * The two phases are:
 * 1. Subscriptions sync
 * 2. Posts sync
 *
 * These tests verify that all phases execute in the correct order and handle
 * various success/failure scenarios appropriately.
 */
class RoverEngageRepositoryTwoPhaseSyncTest : RoverEngageTestBase() {


    // MARK: - Two-Phase Sync Tests

    /**
     * Verify all two phases are synced in the correct order
     */
    @Test
    fun testSubscriptionsSyncBeforePosts() = runTest {
        // Given: Mock responses for all two phases
        val testSubscriptions = createTestSubscriptions(count = 2)
        val testPosts = createTestPosts(count = 3, subscriptionID = testSubscriptions[0].id)

        configureMockForSuccess(
            subscriptions = testSubscriptions,
            posts = testPosts,
        )

        // When: Sync is performed
        val result = repository.sync()

        // Then: All API calls were made in correct order
        assert(result) { "Sync should succeed" }

        // Verify execution order by checking captured requests
        verifyAllApiCallsMadeOnce()

        // Verify data was persisted correctly using real database
        val savedSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }
        val savedPosts = runBlocking { database!!.postsDao().getAllPosts().first() }

        assert(savedSubscriptions.size == 2) { "Should save 2 subscriptions" }
        assert(savedPosts.size == 3) { "Should save 3 posts" }
    }

    /**
     * Verify remaining phases sync continue even if earlier phases fail
     */
    @Test
    fun testPostsSyncContinuesAfterSubscriptionFailure() = runTest {
        // Given: Subscriptions API fails, but other phases succeed
        val testPosts = listOf<PostItem>(TestDataGenerator.createTestPost(id = "test-post-1", subject = "Test Post", subscriptionID = "sub1"))

        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier
        
        // Configure failure for subscriptions but success for others
        doReturn(Response.error<okhttp3.ResponseBody>(500, "Network error".toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        doReturn(Response.success(TestDataGenerator.serializePostsToJson(testPosts).toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())
        
        // When: Sync is performed
        val result = repository.sync()

        // Then: Sync should still succeed because other phases completed
        assert(result) { "Sync should succeed even if subscriptions fail" }

        // Verify all API calls were attempted and execution order
        verifyAllApiCallsMadeOnce()

        // Verify data was persisted correctly using real database
        val savedPosts = runBlocking { database!!.postsDao().getAllPosts().first() }
        val savedSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }
        
        
        assert(savedPosts.size == 1) { "Should save 1 post" }
        // Should have created a placeholder subscription for the post
        assert(savedSubscriptions.size == 1) { "Should create 1 placeholder subscription" }
    }

    /**
     * Verify graceful handling when all two phases fail
     */
    @Test
    fun testBothPhasesFailureHandling() = runTest {
        // Given: All two APIs fail
        configureMockForFailure(
            subscriptionsError = IOException("Network connection lost"),
            postsError = IOException("Bad server response"),
        )

        // When: Sync is performed
        val result = repository.sync()

        // Then: Sync should fail when all phases fail
        assert(!result) { "Sync should fail when all phases fail" }

        // Verify all API calls were attempted and execution order was maintained
        verifyAllApiCallsMadeOnce()

        // Verify no data was saved due to failures using real database
        val savedSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }
        val savedPosts = runBlocking { database!!.postsDao().getAllPosts().first() }

        assert(savedSubscriptions.isEmpty()) { "Should not save any subscriptions" }
        assert(savedPosts.isEmpty()) { "Should not save any posts" }
    }

    /**
     * Verify subscription data from phase 1 is accessible during posts processing
     */
    @Test
    fun testSubscriptionDataAvailableForPostsPhase() = runTest {
        // Given: Subscriptions with specific IDs and posts that reference them
        val testSubscriptions = createTestSubscriptions(count = 3)
        val subscription1ID = testSubscriptions[0].id
        val subscription2ID = testSubscriptions[1].id

        // Create posts that reference the subscriptions with unique IDs
        val postsForSub1 = (0..<2).map { index ->
            createTestPost(
                id = "test-post-sub1-$index",
                subject = "Test Post Sub1 $index",
                subscriptionID = subscription1ID
            )
        }
        val postsForSub2 = (0..<2).map { index ->
            createTestPost(
                id = "test-post-sub2-$index", 
                subject = "Test Post Sub2 $index",
                subscriptionID = subscription2ID
            )
        }
        val allTestPosts = postsForSub1 + postsForSub2

        configureMockForSuccess(
            subscriptions = testSubscriptions,
            posts = allTestPosts,
        )

        // When: Sync is performed
        val result = repository.sync()

        // Then: Sync should succeed and data should be properly linked
        assert(result) { "Sync should succeed" }

        // Verify all data was saved using real database
        val savedSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }
        val savedPosts = runBlocking { database!!.postsDao().getAllPosts().first() }

        
        assert(savedSubscriptions.size == 3) { "Should save 3 subscriptions" }
        assert(savedPosts.size == 4) { "Should save 4 posts" }
    }

    /**
     * Verify that sync returns true when any content phase has data
     */
    @Test
    fun testSyncReturnsTrueWithAnyContentPhase() = runTest {
        val testSubscriptions = createTestSubscriptions(count = 1)

        // Test: Only posts have content
        val testPosts = createTestPosts(count = 2, subscriptionID = testSubscriptions[0].id)

        configureMockForSuccess(
            subscriptions = testSubscriptions,
            posts = testPosts,
        )

        val result3 = repository.sync()
        assert(result3) { "Sync should return true when posts have content" }
    }

    /**
     * Verify that sync returns false when only subscriptions have content
     */
    @Test
    fun testSyncReturnsFalseWithOnlySubscriptions() = runTest {
        val testSubscriptions = createTestSubscriptions(count = 2)

        configureMockForSuccess(
            subscriptions = testSubscriptions,
            posts = emptyList<PostItem>(),
        )

        // When: Sync is performed
        val result = repository.sync()

        // Then: Sync should return false because only subscriptions have content
        assert(!result) { "Sync should return false when only subscriptions have content" }

        // Verify subscriptions were saved using real database
        val savedSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }
        assert(savedSubscriptions.size == 2) { "Should save 2 subscriptions" }
    }

    /**
     * Verify proper handling of mixed success/failure scenarios across phases
     */
    @Test
    fun testMixedPhaseSuccessFailureScenarios() = runTest {
        // Test 1: Subscriptions succeeds, posts fails.
        val testSubscriptions = createTestSubscriptions(count = 1)
        val testPosts = createTestPosts(count = 2, subscriptionID = testSubscriptions[0].id)

        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier

        doReturn(Response.success(TestDataGenerator.serializeSubscriptionsToJson(testSubscriptions).toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        doReturn(Response.error<okhttp3.ResponseBody>(500, "Network error".toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        val result1 = repository.sync()
        assert(!result1) { "Sync should return false when posts fails" }

        // Clear database for next test
        database!!.clearAllTables()
    }
}
