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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.notifications.communicationhub.data.database.RoverEngageDatabase
import io.rover.sdk.notifications.communicationhub.data.dto.PostItem
import io.rover.sdk.notifications.communicationhub.data.dto.SubscriptionItem
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.data.repository.RoverEngageRepository
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.util.*

/**
 * Base test class for tests using the Rover Engage database, providing common setup and helper methods
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
abstract class RoverEngageTestBase {

    // Mock dependencies - shared across all tests
    internal lateinit var mockEngageApiService: EngageApiService
    internal lateinit var mockDeviceIdentification: DeviceIdentificationInterface

    // System under test
    internal lateinit var repository: RoverEngageRepository

    // Database support
    internal var database: RoverEngageDatabase? = null

    @Before
    fun setupBase() {
        setupWithDatabase()
        setupTest()
    }

    @After
    fun teardownBase() {
        database?.close()
    }

    private fun setupWithDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RoverEngageDatabase::class.java
        )
        .allowMainThreadQueries()
        .fallbackToDestructiveMigration(true)
        .build()
        
        database!!.clearAllTables()
        
        mockEngageApiService = mock()
        mockDeviceIdentification = mock()
        
        repository = RoverEngageRepository(
            mockEngageApiService,
            database!!.postsDao(),
            database!!.subscriptionsDao(),
            database!!.cursorsDao(),
            mockDeviceIdentification
        )
    }

    /**
     * Override this method in subclasses for additional test-specific setup
     */
    protected open fun setupTest() {
        // Default implementation does nothing
    }

    // ===== Mock Configuration =====

    /**
     * Configures mocks for successful API responses
     */
    protected suspend fun configureMockForSuccess(
        subscriptions: List<SubscriptionItem> = emptyList(), 
        posts: List<PostItem> = emptyList(),
    ) {
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier
        
        // Setup subscriptions API using actual object serialization
        val subscriptionsJson = if (subscriptions.isNotEmpty()) {
            TestDataGenerator.serializeSubscriptionsToJson(subscriptions)
        } else {
            TestDataGenerator.emptySubscriptionsJson()
        }
        doReturn(Response.success(subscriptionsJson.toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        // Setup posts API using actual object serialization
        val postsJson = if (posts.isNotEmpty()) {
            TestDataGenerator.serializePostsToJson(posts)
        } else {
            TestDataGenerator.emptyPostsJson()
        }
        doReturn(Response.success(postsJson.toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())
    }
    
    /**
     * Configures mocks for failed API responses with basic error
     */
    protected suspend fun configureMockForFailure() {
        configureMockForFailure(
            subscriptionsError = RuntimeException("Subscriptions API failed"),
            postsError = RuntimeException("Posts API failed"),
        )
    }
    
    /**
     * Configures mocks for failure scenarios across all two phases with specific errors
     */
    protected suspend fun configureMockForFailure(
        subscriptionsError: Throwable,
        postsError: Throwable,
    ) {
        // Setup device identification
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier

        // Setup all APIs to fail
        doReturn(Response.error<okhttp3.ResponseBody>(500, (subscriptionsError.message ?: "Network error").toResponseBody(null)))
            .whenever(mockEngageApiService).getSubscriptions()

        doReturn(Response.error<okhttp3.ResponseBody>(500, (postsError.message ?: "Network error").toResponseBody(null)))
            .whenever(mockEngageApiService).getPosts(eq("test-device-id"), anyOrNull())
    }
    

    /**
     * Verifies that all API endpoints were called exactly once
     */
    protected suspend fun verifyAllApiCallsMadeOnce() {
        verify(mockEngageApiService, times(1)).getSubscriptions()
        verify(mockEngageApiService, times(1)).getPosts(eq("test-device-id"), eq(null))
    }

    // ===== Test Data Creation =====

    /**
     * Creates test data using TestDataGenerator
     */
    protected fun createTestSubscriptions(count: Int = 2): List<SubscriptionItem> = 
        TestDataGenerator.createTestSubscriptions(count)

    protected fun createTestPosts(count: Int = 3, subscriptionID: String? = null): List<PostItem> = 
        TestDataGenerator.createTestPosts(count, subscriptionID)

    /**
     * Creates individual test items with customizable properties
     */
    protected fun createTestPost(
        id: String = "test-post-${UUID.randomUUID()}",
        subject: String = "Test Post",
        subscriptionID: String? = null,
        isRead: Boolean = false
    ): PostItem = PostItem(
        id = id,
        subject = subject,
        previewText = "Test preview text for $subject",
        receivedAt = Date(System.currentTimeMillis()),
        url = "https://example.com/$id",
        coverImageURL = "https://example.com/$id.jpg",
        subscriptionID = subscriptionID,
        isRead = isRead
    )

    protected fun createTestSubscription(
        id: String = "sub-${UUID.randomUUID()}",
        name: String = "Test Subscription",
        optIn: Boolean = false,
        status: String = "published"
    ): SubscriptionItem = SubscriptionItem(
        id = id,
        name = name,
        description = "Test description for $name",
        optIn = optIn,
        status = status
    )

    // ===== Database Helpers =====

    /**
     * Creates and inserts standard test data into database
     */

    protected suspend fun createStandardTestSubscriptions(): List<SubscriptionItem> {
        require(database != null) { "createStandardTestSubscriptions() requires database support." }
        
        val subscriptions = listOf(
            createTestSubscription(id = "sub-1", name = "Sub 1", optIn = true, status = "published"),
            createTestSubscription(id = "sub-2", name = "Sub 2", optIn = false, status = "archived"),
            createTestSubscription(id = "sub-3", name = "Sub 3", optIn = true, status = "unpublished")
        )

        val subscriptionsDao = database!!.subscriptionsDao()
        subscriptions.forEach { subscription ->
            subscriptionsDao.upsertSubscription(subscription.toEntity())
        }

        return subscriptions
    }

    // ===== Time Helpers =====

    protected fun currentTimeMillis(): Long = System.currentTimeMillis()
    protected fun futureTimeMillis(): Long = currentTimeMillis() + 86400000
    protected fun pastTimeMillis(): Long = currentTimeMillis() - 86400000
    protected fun currentDate(): Date = Date(currentTimeMillis())
    protected fun futureDate(): Date = Date(futureTimeMillis())
    protected fun pastDate(): Date = Date(pastTimeMillis())

    // ===== Helper Methods =====

    /**
     * Creates a unique post ID for testing
     */
    protected fun createUniquePostId(): String = "test-post-${UUID.randomUUID()}"

    /**
     * Configures basic mocks to prevent null pointer exceptions
     */
    protected suspend fun setupBasicMocks() {
        doReturn("test-device-id").whenever(mockDeviceIdentification).installationIdentifier
        doReturn(Response.success(TestDataGenerator.emptySubscriptionsJson().toResponseBody(null))).whenever(mockEngageApiService).getSubscriptions()
        doReturn(Response.success(TestDataGenerator.emptyPostsJson().toResponseBody(null))).whenever(mockEngageApiService).getPosts(eq("test-device-id"), eq(null))
    }

    /**
     * Asserts that a post has the expected properties
     */
    protected fun assertPostProperties(
        post: io.rover.sdk.notifications.communicationhub.data.database.entities.PostEntity?,
        expectedSubject: String,
        expectedIsRead: Boolean? = null,
        expectedSubscriptionId: String? = null
    ) {
        assert(post != null) { "Post should not be null" }
        assert(post?.subject == expectedSubject) { "Post subject should be '$expectedSubject' but was '${post?.subject}'" }
        
        if (expectedIsRead != null) {
            assert(post?.isRead == expectedIsRead) { "Post isRead should be $expectedIsRead but was ${post?.isRead}" }
        }
        
        if (expectedSubscriptionId != null) {
            assert(post?.subscriptionId == expectedSubscriptionId) { "Post subscriptionId should be '$expectedSubscriptionId' but was '${post?.subscriptionId}'" }
        }
    }
}
