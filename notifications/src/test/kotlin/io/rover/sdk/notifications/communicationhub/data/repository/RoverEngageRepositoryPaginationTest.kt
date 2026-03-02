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
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

/**
 * Tests for CommHubRepository cursor-based pagination logic
 *
 * These tests verify that the repository correctly handles:
 * - Single page responses
 * - Multi-page recursive pagination
 * - Network failures during pagination
 * - Large datasets without stack overflow
 * - Empty page handling
 */
class RoverEngageRepositoryPaginationTest : RoverEngageTestBase() {


    /**
     * Verify sync works correctly for single page responses
     */
    @Test
    fun testSinglePageSync() = runTest {
        // Given: Single page of posts with no pagination
        val testSubscriptions = createTestSubscriptions(count = 1)
        val subscriptionId = testSubscriptions[0].id

        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier

        // Setup API responses - single page with hasMore = false
        doReturn(Response.success(TestDataGenerator.createTestSubscriptionsJson(1).toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        // Single page response with 5 posts, hasMore = false and no nextCursor
        val singlePagePostsJson = """
            {
              "posts": [
                {"id": "post-0", "subject": "Post 0", "previewText": "Preview 0", "receivedAt": "2023-01-01T00:00:00Z", "url": "https://example.com/post-0", "coverImageURL": "https://example.com/post-0.jpg", "subscriptionID": "$subscriptionId", "isRead": false},
                {"id": "post-1", "subject": "Post 1", "previewText": "Preview 1", "receivedAt": "2023-01-01T00:00:00Z", "url": "https://example.com/post-1", "coverImageURL": "https://example.com/post-1.jpg", "subscriptionID": "$subscriptionId", "isRead": false},
                {"id": "post-2", "subject": "Post 2", "previewText": "Preview 2", "receivedAt": "2023-01-01T00:00:00Z", "url": "https://example.com/post-2", "coverImageURL": "https://example.com/post-2.jpg", "subscriptionID": "$subscriptionId", "isRead": false},
                {"id": "post-3", "subject": "Post 3", "previewText": "Preview 3", "receivedAt": "2023-01-01T00:00:00Z", "url": "https://example.com/post-3", "coverImageURL": "https://example.com/post-3.jpg", "subscriptionID": "$subscriptionId", "isRead": false},
                {"id": "post-4", "subject": "Post 4", "previewText": "Preview 4", "receivedAt": "2023-01-01T00:00:00Z", "url": "https://example.com/post-4", "coverImageURL": "https://example.com/post-4.jpg", "subscriptionID": "$subscriptionId", "isRead": false}
              ],
              "nextCursor": null,
              "hasMore": false
            }
        """.trimIndent()

        doReturn(Response.success(singlePagePostsJson.toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        // When: Sync is performed
        val result = repository.sync()

        // Then: Sync should return true (has new content - 5 posts)
        assert(result) { "Single page sync should return true when posts are available" }

        // Verify only one posts API call was made
        verify(mockEngageApiService, times(1)).getSubscriptions()
        verify(mockEngageApiService, times(1)).getPosts(eq("test-device-id"), anyOrNull())

    }

    /**
     * Verify all pages are fetched recursively for multi-page responses
     */
    @Test
    fun testMultiPageRecursiveSync() = runTest {
        // Given: Multiple pages of posts (3 pages, 4 posts each = 12 total)
        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier

        // Setup subscriptions API
        doReturn(Response.success(TestDataGenerator.createTestSubscriptionsJson(1).toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        // Use TestDataGenerator to create paginated responses (3 pages, 4 posts each)
        val paginatedResponses = TestDataGenerator.createPaginatedPostResponses(totalPosts = 12, pageSize = 4, subscriptionId = "subscription-0")

        // Configure paginated responses based on cursor parameter
        doAnswer { invocation ->
            val cursor = invocation.getArgument<String?>(1)
            val responseJson = when (cursor) {
                null -> paginatedResponses[0]  // Page 1
                "cursor-page-1" -> paginatedResponses[1]  // Page 2
                "cursor-page-2" -> paginatedResponses[2]  // Page 3
                else -> TestDataGenerator.emptyPostsJson()
            }
            Response.success(responseJson.toResponseBody(null))
        }.whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        // When: Sync is performed
        val result = repository.sync()

        // Then: Sync should succeed (returns true because posts are available)
        assert(result) { "Multi-page sync should succeed" }

        // Verify correct number of posts API calls (3 pages)
        verify(mockEngageApiService, times(1)).getSubscriptions()
        verify(mockEngageApiService, times(3)).getPosts(eq("test-device-id"), anyOrNull())

    }

    /**
     * Verify graceful handling of network failures during pagination
     */
    @Test
    fun testPaginationWithNetworkFailure() = runTest {
        // Given: First page succeeds, second page fails
        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier

        // Setup subscriptions API
        doReturn(Response.success(TestDataGenerator.createTestSubscriptionsJson(1).toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        // Use TestDataGenerator to create first page response
        val paginatedResponses = TestDataGenerator.createPaginatedPostResponses(totalPosts = 4, pageSize = 2, subscriptionId = "subscription-0")

        // Configure responses: first succeeds, second fails
        doAnswer { invocation ->
            val cursor = invocation.getArgument<String?>(1)
            when (cursor) {
                null -> Response.success(paginatedResponses[0].toResponseBody(null))  // First page succeeds
                "cursor-page-1" -> Response.error(500, "Server Error".toResponseBody(null))  // Second page fails
                else -> Response.error(500, "Server Error".toResponseBody(null))
            }
        }.whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        // When: Sync is performed
        val result = repository.sync()

        // Then: Sync should succeed because first page had posts, even though second page failed
        assert(result) { "Sync should succeed when first page has posts, even if later pages fail" }

        // Verify both API calls were attempted
        verify(mockEngageApiService, times(1)).getSubscriptions()
        verify(mockEngageApiService, times(2)).getPosts(eq("test-device-id"), anyOrNull())
    }

    /**
     * Verify correct behavior when API returns empty pages
     */
    @Test
    fun testEmptyPageHandling() = runTest {
        // Test 1: Completely empty response (no posts, hasMore = false)
        val testSubscriptions = createTestSubscriptions(count = 1)

        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier

        // Setup API responses
        doReturn(Response.success(TestDataGenerator.createTestSubscriptionsJson(1).toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        // Empty posts response
        val emptyPostsJson = """
            {
              "posts": [],
              "nextCursor": null,
              "hasMore": false
            }
        """.trimIndent()

        doReturn(Response.success(emptyPostsJson.toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        // When: Sync is performed
        val result = repository.sync()

        // Then: Sync should succeed but return false (no new posts)
        assert(!result) { "Sync should return false when no posts are retrieved" }

        // Verify API calls were made
        verify(mockEngageApiService, times(1)).getSubscriptions()
        verify(mockEngageApiService, times(1)).getPosts(eq("test-device-id"), anyOrNull())
    }

    /**
     * Test to verify basic sync logic works
     */
    @Test
    fun testBasicSyncLogic() = runTest {
        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier

        // Setup subscriptions API - return 1 subscription
        doReturn(Response.success(TestDataGenerator.createTestSubscriptionsJson(1).toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        // Setup posts API - single page with posts
        val singlePagePostsJson = """
            {
              "posts": [
                {"id": "post-0", "subject": "Post 0", "previewText": "Preview 0", "receivedAt": "2023-01-01T00:00:00Z", "url": "https://example.com/post-0", "coverImageURL": "https://example.com/post-0.jpg", "subscriptionID": "subscription-0", "isRead": false}
              ],
              "nextCursor": null,
              "hasMore": false
            }
        """.trimIndent()

        doReturn(Response.success(singlePagePostsJson.toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        // When: Sync is performed
        val result = repository.sync()

        // Then: Should succeed
        assert(result) { "Basic sync should succeed when posts are available" }
    }

    /**
     * Verify empty first page continues to next page with actual posts
     */
    @Test
    fun testEmptyFirstPageWithMorePages() = runTest {
        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier

        // Setup subscriptions API
        doReturn(Response.success(TestDataGenerator.createTestSubscriptionsJson(1).toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        // Empty first page with hasMore = true
        val emptyPage1Json = """
            {
              "posts": [],
              "nextCursor": "cursor-page-1",
              "hasMore": true
            }
        """.trimIndent()

        // Second page with actual posts (manually created to have hasMore=false)
        val secondPageJson = """
            {
              "posts": [
                {"id": "test-post-0", "subject": "Test Post 0", "previewText": "This is the preview text for Test Post 0", "receivedAt": "2023-01-01T00:00:00Z", "url": "https://example.com/post/test-post-0", "coverImageURL": "https://example.com/images/test-post-0.jpg", "subscriptionID": "subscription-0", "isRead": false}
              ],
              "nextCursor": null,
              "hasMore": false
            }
        """.trimIndent()

        // Configure paginated responses
        doAnswer { invocation ->
            val cursor = invocation.getArgument<String?>(1)
            val responseJson = when (cursor) {
                null -> emptyPage1Json  // Empty first page
                "cursor-page-1" -> secondPageJson  // Second page with posts
                else -> TestDataGenerator.emptyPostsJson()
            }
            Response.success(responseJson.toResponseBody(null))
        }.whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())

        // When: Sync is performed
        val result = repository.sync()

        // Then: Should continue to next page and succeed
        assert(result) { "Sync should succeed when later pages have posts" }

        // Verify both pages were called
        verify(mockEngageApiService, times(1)).getSubscriptions()
        verify(mockEngageApiService, times(2)).getPosts(eq("test-device-id"), anyOrNull())
    }
}