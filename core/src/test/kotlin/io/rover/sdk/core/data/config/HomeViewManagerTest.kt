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

package io.rover.sdk.core.data.config

import android.content.Context
import com.squareup.moshi.Moshi
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.core.platform.KeyValueStorage
import io.rover.sdk.core.platform.LocalStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeViewManagerTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var engageApiService: EngageApiService
    private lateinit var localStorage: LocalStorage
    private lateinit var mockUserInfo: UserInfoInterface
    private lateinit var mockDeviceIdentification: DeviceIdentificationInterface
    private lateinit var mockStorage: KeyValueStorage

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        // Create EngageApiService with mocked dependencies
        val mockContext = mock<Context>()
        val mockPackageManager = mock<android.content.pm.PackageManager>()
        val mockPackageInfo = mock<android.content.pm.PackageInfo>().apply {
            versionName = "1.0.0"
        }
        
        whenever(mockContext.packageName).thenReturn("io.rover.test")
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.getPackageInfo("io.rover.test", 0)).thenReturn(mockPackageInfo)
        
        val mockAuthContext = mock<AuthenticationContextInterface>()
        whenever(mockAuthContext.sdkToken).thenReturn("test-token")
        
        val httpClient = EngageHttpClient(mockContext, mockAuthContext)
        engageApiService = EngageApiService.create(httpClient, mockServer.url("/").toString())

        mockStorage = mock<KeyValueStorage>()
        localStorage = mock<LocalStorage>()
        whenever(localStorage.getKeyValueStorageFor("home-view")).thenReturn(mockStorage)

        mockUserInfo = mock<UserInfoInterface>()
        whenever(mockUserInfo.currentUserInfo).thenReturn(hashMapOf())

        mockDeviceIdentification = mock<DeviceIdentificationInterface>()
        whenever(mockDeviceIdentification.installationIdentifier).thenReturn("test-device-id")
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    // MARK: - Initialization Tests

    @Test
    fun `init loads nil when no cached URL`() = runTest {
        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(null)

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        assertNull(manager.experienceURL.first())
    }

    @Test
    fun `init loads cached URL from storage`() = runTest {
        val cachedResponse = HomeViewResponse(experienceURL = "https://cached.rover.io/experience")
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(HomeViewResponse::class.java)
        val json = adapter.toJson(cachedResponse)

        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(json)

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        assertEquals("https://cached.rover.io/experience", manager.experienceURL.first())
    }

    // MARK: - Fetch Tests

    @Test
    fun `fetch updates experienceURL on success`() = runTest {
        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(null)

        val json = """
            { "experienceURL": "https://new.rover.io/experience" }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        manager.fetch()

        assertEquals("https://new.rover.io/experience", manager.experienceURL.first())
    }

    @Test
    fun `fetch saves URL to storage`() = runTest {
        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(null)

        val json = """
            { "experienceURL": "https://saved.rover.io/experience" }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        manager.fetch()

        // Verify save was called with the response
        org.mockito.kotlin.verify(mockStorage).set(
            org.mockito.kotlin.eq(HomeViewManager.STORAGE_KEY),
            org.mockito.kotlin.any()
        )
    }

    @Test
    fun `fetch saves null URL to storage`() = runTest {
        val cachedResponse = HomeViewResponse(experienceURL = "https://old.rover.io/experience")
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(HomeViewResponse::class.java)
        val json = adapter.toJson(cachedResponse)

        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(json)

        val responseJson = """
            { "experienceURL": null }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        manager.fetch()

        assertNull(manager.experienceURL.first())
        org.mockito.kotlin.verify(mockStorage).set(
            org.mockito.kotlin.eq(HomeViewManager.STORAGE_KEY),
            org.mockito.kotlin.any()
        )
    }

    // MARK: - Failure Handling Tests

    @Test
    fun `fetch failure leaves experienceURL as nil`() = runTest {
        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(null)

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        manager.fetch()

        assertNull(manager.experienceURL.first())
    }

    @Test
    fun `fetch failure preserves cached URL`() = runTest {
        val cachedResponse = HomeViewResponse(experienceURL = "https://cached.rover.io/experience")
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(HomeViewResponse::class.java)
        val cachedJson = adapter.toJson(cachedResponse)

        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(cachedJson)

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        // Initial value should be cached
        assertEquals("https://cached.rover.io/experience", manager.experienceURL.first())

        manager.fetch()

        // Should still be cached after failed fetch
        assertEquals("https://cached.rover.io/experience", manager.experienceURL.first())
    }

    // MARK: - Identifier Resolution Tests

    @Test
    fun `fetch passes userID when available`() = runTest {
        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(null)
        whenever(mockUserInfo.currentUserInfo).thenReturn(hashMapOf("userID" to "user-abc"))

        val json = """
            { "experienceURL": "https://example.com" }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        manager.fetch()

        val request = mockServer.takeRequest()
        assertEquals("/home?userID=user-abc", request.path)
    }

    @Test
    fun `fetch passes ticketmasterID when no direct userID`() = runTest {
        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(null)
        whenever(mockUserInfo.currentUserInfo).thenReturn(
            hashMapOf("ticketmaster.ticketmasterID" to "tm-123")
        )

        val json = """
            { "experienceURL": "https://example.com" }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        manager.fetch()

        val request = mockServer.takeRequest()
        assertEquals("/home?userID=tm-123", request.path)
    }

    @Test
    fun `fetch passes seatGeekID when no other userID`() = runTest {
        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(null)
        whenever(mockUserInfo.currentUserInfo).thenReturn(
            hashMapOf("seatGeek.seatGeekID" to "sg-456")
        )

        val json = """
            { "experienceURL": "https://example.com" }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        manager.fetch()

        val request = mockServer.takeRequest()
        assertEquals("/home?userID=sg-456", request.path)
    }

    @Test
    fun `fetch prefers direct userID over ticketmaster`() = runTest {
        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(null)
        whenever(mockUserInfo.currentUserInfo).thenReturn(
            hashMapOf(
                "userID" to "direct-user",
                "ticketmaster.ticketmasterID" to "tm-123"
            )
        )

        val json = """
            { "experienceURL": "https://example.com" }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        manager.fetch()

        val request = mockServer.takeRequest()
        assertEquals("/home?userID=direct-user", request.path)
    }

    @Test
    fun `fetch passes deviceIdentifier when no userID`() = runTest {
        whenever(mockStorage.get(HomeViewManager.STORAGE_KEY)).thenReturn(null)
        whenever(mockUserInfo.currentUserInfo).thenReturn(hashMapOf())

        val json = """
            { "experienceURL": "https://example.com" }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(json)
                .addHeader("Content-Type", "application/json")
        )

        val manager = HomeViewManager(
            engageApiService = engageApiService,
            localStorage = localStorage,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification
        )

        manager.fetch()

        val request = mockServer.takeRequest()
        assertEquals("/home?deviceIdentifier=test-device-id", request.path)
    }
}
