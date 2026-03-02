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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

/**
 * Tests for CommHub sync triggers and SDK integration
 * 
 * These tests verify how the sync is triggered in different app lifecycle scenarios
 * and how it behaves under various network conditions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoverEngageRepositorySyncTriggerTest : RoverEngageTestBase() {
    // MARK: - Sync Trigger Tests

    /**
     * Test sync initiation during app launch lifecycle
     */
    @Test
    fun testAppLaunchSync() = runTest {
        // Configure mock for successful sync with no new content
        configureMockForSuccess(
            subscriptions = emptyList(),
            posts = emptyList(),
        )

        // Simulate app launch by directly calling sync on repository
        // In real app, this would be triggered by SyncCoordinator during app launch
        val syncResult = repository.sync()

        // Verify sync completed (returns false when no new posts available)
        assert(!syncResult) { "App launch sync should return false when no new posts are available" }

        // Verify sync was called in correct order (subscriptions → posts)
        verifyAllApiCallsMadeOnce()

        // Test that database is properly accessible during app launch sync
        // This is handled internally by the repository's sync() method
        val savedSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }
        val savedPosts = runBlocking { database!!.postsDao().getAllPosts().first() }

        assert(savedSubscriptions.isEmpty()) { "No subscriptions should be saved during app launch with no content" }
        assert(savedPosts.isEmpty()) { "No posts should be saved during app launch with no content" }
    }

    /**
     * Test sync triggered by silent push notifications
     */
    @Test
    fun testBackgroundPushSync() = runTest {
        // Configure mock for successful background sync
        val testSubscriptions = createTestSubscriptions(count = 2)
        val testPosts = createTestPosts(count = 3, subscriptionID = testSubscriptions[0].id)

        configureMockForSuccess(
            subscriptions = testSubscriptions,
            posts = testPosts,
        )

        // Simulate background push notification triggering sync
        // In real app, this would be triggered by SyncCoordinator.sync(completionHandler:)
        // when a silent push notification is received
        val syncResult = repository.sync()

        // Verify background sync was successful
        assert(syncResult) { "Background push sync should succeed" }

        // Verify data was synced from the "background" API call
        val savedSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }
        val savedPosts = runBlocking { database!!.postsDao().getAllPosts().first() }

        assert(savedSubscriptions.size == 2) { "Background sync should save 2 subscriptions" }
        assert(savedPosts.size == 3) { "Background sync should save 3 posts" }

        // Verify background sync respects the two-phase sequence
        verifyAllApiCallsMadeOnce()

        // Test that background sync can handle partial failures gracefully
        database!!.clearAllTables()
        
        // Reset mocks to avoid verification conflicts
        reset(mockEngageApiService)
        
        // Configure failure for subscriptions but success for others
        // Use configureMockForSuccess with proper test data instead of hardcoded values
        val partialTestPosts = createTestPosts(count = 1, subscriptionID = "sub1")

        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier
        
        // Configure failure for subscriptions but success for others
        doReturn(Response.error<okhttp3.ResponseBody>(500, "Network error".toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        doReturn(Response.success(TestDataGenerator.serializePostsToJson(partialTestPosts).toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        val partialSyncResult = repository.sync()
        assert(partialSyncResult) { "Background sync should succeed when content phases succeed" }

        // Verify all phases continued despite subscriptions failure
        verifyAllApiCallsMadeOnce()
    }

    /**
     * Test sync triggered by manual UI refresh actions
     */
    @Test
    fun testManualRefreshSync() = runTest {
        // Set up initial data
        val initialSubscriptions = createTestSubscriptions(count = 1)
        val initialPosts = createTestPosts(count = 2, subscriptionID = initialSubscriptions[0].id)

        configureMockForSuccess(
            subscriptions = initialSubscriptions,
            posts = initialPosts,
        )

        // Perform initial sync
        val initialResult = repository.sync()
        assert(initialResult) { "Initial sync should succeed" }

        // Verify initial data was saved
        val initialSavedPosts = runBlocking { database!!.postsDao().getAllPosts().first() }
        assert(initialSavedPosts.size == 2) { "Initial sync should save 2 posts" }

        // Configure mock for manual refresh with new data
        val newSubscriptions = createTestSubscriptions(count = 2)
        val newPosts = createTestPosts(count = 3, subscriptionID = initialSubscriptions[0].id) // Use same subscription ID

        // Setup device identification for new sync
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier

        doReturn(Response.success(TestDataGenerator.serializeSubscriptionsToJson(newSubscriptions).toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        doReturn(Response.success(TestDataGenerator.serializePostsToJson(newPosts).toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        // Simulate manual refresh by calling sync again
        // In real app, this would be triggered by CommunicationHubView.refreshPosts()
        // which calls SyncCoordinator.syncAsync()
        val refreshResult = repository.sync()

        // Verify manual refresh was successful
        assert(refreshResult) { "Manual refresh sync should succeed with new data" }

        // Verify new data was retrieved and saved (accumulated with existing content)
        val allSavedPosts = runBlocking { database!!.postsDao().getAllPosts().first() }
        val allSavedSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }
        
        
        assert(allSavedPosts.size == 3) { 
            "Manual refresh should upsert posts (3 new posts, replacing the 2 initial posts)" 
        }
        assert(allSavedSubscriptions.size == 2) {
            "Manual refresh should save subscriptions (2 new subscriptions, replacing the 1 initial)" 
        }

        // Test manual refresh with no new data
        // Reset mocks to avoid verification conflicts
        reset(mockEngageApiService)
        
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier
        
        doReturn(Response.success(TestDataGenerator.emptySubscriptionsJson().toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        doReturn(Response.success(TestDataGenerator.emptyPostsJson().toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        val noDataRefreshResult = repository.sync()
        assert(!noDataRefreshResult) { "Manual refresh should return false when no new data is available" }
    }

    /**
     * Test sync behavior when network is unavailable (offline queueing)
     */
    @Test
    fun testOfflineQueueing() = runTest {
        // Test sync failure when network is completely unavailable
        configureMockForFailure(
            subscriptionsError = IOException("Not connected to internet"),
            postsError = IOException("Not connected to internet"),
        )

        val offlineSyncResult = repository.sync()

        // Verify sync fails gracefully when offline
        assert(!offlineSyncResult) { "Sync should return false when network is unavailable" }

        // Verify all two API calls were attempted
        verifyAllApiCallsMadeOnce()

        // Verify no data was saved due to network failure
        val offlinePosts = runBlocking { database!!.postsDao().getAllPosts().first() }
        val offlineSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }

        assert(offlinePosts.isEmpty()) { "No posts should be saved when network is unavailable" }
        assert(offlineSubscriptions.isEmpty()) { "No subscriptions should be saved when network is unavailable" }

        // Test partial network availability (subscriptions fail, other phases succeed)
        val offlineTestPosts = createTestPosts(count = 2, subscriptionID = "placeholder-sub")

        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier
        
        doReturn(Response.error<okhttp3.ResponseBody>(500, "Network timeout".toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        doReturn(Response.success(TestDataGenerator.serializePostsToJson(offlineTestPosts).toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        val partialNetworkResult = repository.sync()

        // Verify sync succeeds when content APIs are available (even if subscriptions fail)
        assert(partialNetworkResult) { "Sync should succeed when content APIs are available" }

        // Verify content was saved despite subscriptions failure
        val partialPosts = runBlocking { database!!.postsDao().getAllPosts().first() }
        val partialSubscriptions = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }
        
        assert(partialPosts.size == 2) { "Posts should be saved when posts API is available" }

        // Verify placeholder subscription was created for orphaned content
        assert(partialSubscriptions.size == 1) { "Placeholder subscription should be created for orphaned content" }
        assert(partialSubscriptions[0].id == "placeholder-sub") { "Placeholder subscription should have correct ID" }

        // Test network recovery scenario
        val recoverySubscriptions = createTestSubscriptions(count = 3)
        val recoveryPosts = createTestPosts(count = 4, subscriptionID = recoverySubscriptions[0].id)

        doReturn(Response.success(TestDataGenerator.serializeSubscriptionsToJson(recoverySubscriptions).toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        doReturn(Response.success(TestDataGenerator.serializePostsToJson(recoveryPosts).toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())
        
        val recoveryResult = repository.sync()

        // Verify sync succeeds when network is restored
        assert(recoveryResult) { "Sync should succeed when network is restored" }

        // Verify new data is properly synced after network recovery (upserted content)
        val recoveryAllPosts = runBlocking { database!!.postsDao().getAllPosts().first() }
        val recoveryAllSubs = runBlocking { database!!.subscriptionsDao().getAllSubscriptions() }

        // Content is upserted: new content replaces existing content with same IDs
        assert(recoveryAllPosts.size == 4) { 
            "New posts should be saved after network recovery (4 new posts, replacing the 2 partial posts)" 
        }

        assert(recoveryAllSubs.size == 4) {
            "New subscriptions should be saved after network recovery (3 new subscriptions + 1 existing placeholder with different ID)" 
        }
    }
}
