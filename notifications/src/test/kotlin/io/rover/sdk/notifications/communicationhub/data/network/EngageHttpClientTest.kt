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

package io.rover.sdk.notifications.communicationhub.data.network

import android.content.Context
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.notifications.communicationhub.conversations.dto.ReplyContentBlockItem
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EngageHttpClientTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var httpClient: EngageHttpClient
    private lateinit var mockUserInfo: UserInfoInterface
    private lateinit var mockDeviceIdentification: DeviceIdentificationInterface
    private lateinit var authenticationContext: FakeAuthenticationContext

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        val mockContext = mock<Context>()
        val mockPackageManager = mock<android.content.pm.PackageManager>()
        val mockPackageInfo = mock<android.content.pm.PackageInfo>().apply {
            versionName = "1.0.0"
        }

        whenever(mockContext.packageName).thenReturn("io.rover.test")
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.getPackageInfo("io.rover.test", 0)).thenReturn(mockPackageInfo)

        mockUserInfo = mock<UserInfoInterface>()
        whenever(mockUserInfo.currentUserInfo).thenReturn(hashMapOf("userID" to "user-123"))

        mockDeviceIdentification = mock<DeviceIdentificationInterface>()
        whenever(mockDeviceIdentification.installationIdentifier).thenReturn("device-123")

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = null,
            sdkAuthenticationEnabledDomains = emptySet()
        )

        httpClient = EngageHttpClient(
            context = mockContext,
            accountToken = "account-token",
            authenticationContext = authenticationContext,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification,
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `create conversation includes deviceIdentifier userID and account token when sdk auth not enabled`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/conversations?deviceIdentifier=device-123&userID=user-123", request.path)
        assertEquals("account-token", request.getHeader("x-rover-account-token"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `create conversation includes userID when sdk auth is enabled for domain`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/conversations?deviceIdentifier=device-123&userID=user-123", request.path)
        assertEquals("account-token", request.getHeader("x-rover-account-token"))
    }

    @Test
    fun `send reply includes deviceIdentifier userID and account token when sdk auth not enabled`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations/conversation-1/replies"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            "/conversations/conversation-1/replies?deviceIdentifier=device-123&userID=user-123",
            request.path
        )
        assertEquals("account-token", request.getHeader("x-rover-account-token"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `send reply includes userID when sdk auth is enabled for domain`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations/conversation-1/replies"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            "/conversations/conversation-1/replies?deviceIdentifier=device-123&userID=user-123",
            request.path
        )
        assertEquals("account-token", request.getHeader("x-rover-account-token"))
    }

    @Test
    fun `conversations request includes bearer token when sdk auth matches domain`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertEquals("Bearer id-token-123", request.getHeader("Authorization"))
        assertEquals("account-token", request.getHeader("x-rover-account-token"))
    }

    @Test
    fun `conversations request skips sdk auth when sdk auth matches domain but userID is missing`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        whenever(mockUserInfo.currentUserInfo).thenReturn(hashMapOf("email" to "person@example.com"))
        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("deviceIdentifier=device-123"))
        assertFalse(request.path!!.contains("userID="))
        assertNull(request.getHeader("Authorization"))
        assertEquals(0, authenticationContext.idTokenLookupCount)
    }

    @Test
    fun `conversations request skips sdk auth when sdk auth matches domain but userID is blank`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        whenever(mockUserInfo.currentUserInfo).thenReturn(hashMapOf("userID" to "   "))
        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("deviceIdentifier=device-123"))
        assertFalse(request.path!!.contains("userID="))
        assertNull(request.getHeader("Authorization"))
        assertEquals(0, authenticationContext.idTokenLookupCount)
    }

    @Test
    fun `conversations request uses one eligible userID value for decoration and auth`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        whenever(mockUserInfo.currentUserInfo).thenReturn(
            hashMapOf("userID" to "user-123"),
            hashMapOf("email" to "person@example.com")
        )
        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("userID=user-123"))
        assertEquals("Bearer id-token-123", request.getHeader("Authorization"))
        assertEquals(1, authenticationContext.idTokenLookupCount)
    }

    @Test
    fun `conversations request uses ticketmasterID when no userID and sdk auth enabled`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        whenever(mockUserInfo.currentUserInfo).thenReturn(
            hashMapOf("ticketmaster" to hashMapOf("ticketmasterID" to "tm-456"))
        )
        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("userID=tm-456"))
        assertEquals("Bearer id-token-123", request.getHeader("Authorization"))
    }

    @Test
    fun `conversations request uses seatGeekClientID when no userID or ticketmaster and sdk auth enabled`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        whenever(mockUserInfo.currentUserInfo).thenReturn(
            hashMapOf("seatGeek" to hashMapOf("seatGeekClientID" to "sg-client-789"))
        )
        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("userID=sg-client-789"))
        assertEquals("Bearer id-token-123", request.getHeader("Authorization"))
    }

    @Test
    fun `conversations request keeps userID when token is absent after auth lookup`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = null,
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("userID=user-123"))
        assertNull(request.getHeader("Authorization"))
        assertEquals(1, authenticationContext.idTokenLookupCount)
    }

    @Test
    fun `engage api service sends reply request body with content field`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(202).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )

        val apiService = EngageApiService.create(httpClient(), mockServer.url("/").toString())

        apiService.sendConversationReply(
            conversationId = "conversation-1",
            body = SendConversationReplyRequest(
                content = listOf(ReplyContentBlockItem.text("Hello")),
                externalID = "external-123"
            )
        )

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("/conversations/conversation-1/replies?deviceIdentifier=device-123&userID=user-123", request.path)
        assertTrue(body.contains("\"content\":[{"))
        assertTrue(body.contains("\"externalID\":\"external-123\""))
        assertFalse(body.contains("\"blocks\""))
        assertFalse(body.contains("\"externalId\""))
    }

    @Test
    fun `engage api service includes participants when requesting conversations`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )

        val apiService = EngageApiService.create(httpClient(), mockServer.url("/").toString())

        apiService.getConversations()

        val request = mockServer.takeRequest()
        assertEquals(
            "/conversations?include=participants&deviceIdentifier=device-123&userID=user-123",
            request.path
        )
    }

    @Test
    fun `participants request includes deviceIdentifier and userID when sdk auth is enabled`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )

        val apiService = EngageApiService.create(httpClient(), mockServer.url("/").toString())

        apiService.getParticipants()

        val request = mockServer.takeRequest()
        assertEquals("/participants?deviceIdentifier=device-123&userID=user-123", request.path)
        assertEquals("Bearer id-token-123", request.getHeader("Authorization"))
    }

    @Test
    fun `participants request includes userID and omits bearer token when sdk auth is disabled`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val apiService = EngageApiService.create(httpClient, mockServer.url("/").toString())

        apiService.getParticipants()

        val request = mockServer.takeRequest()
        assertEquals("/participants?deviceIdentifier=device-123&userID=user-123", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `engage api service omits lastReadReplyID when mark read checkpoint is absent`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(202).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )

        val apiService = EngageApiService.create(httpClient(), mockServer.url("/").toString())

        apiService.markConversationRead(
            conversationId = "conversation-1",
            body = MarkConversationReadRequest(lastReadReplyId = null)
        )

        val request = mockServer.takeRequest()
        val body = request.body.readUtf8()
        assertEquals(
            "/conversations/conversation-1/read?deviceIdentifier=device-123&userID=user-123",
            request.path
        )
        assertFalse(body.contains("lastReadReplyID"))
    }

    @Test
    fun `engage api service decorates conversations when base url includes path prefix`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = "id-token-123",
            sdkAuthenticationEnabledDomains = setOf(mockServer.url("/").host)
        )

        val apiService = EngageApiService.create(httpClient(), mockServer.url("/engage/").toString())

        apiService.sendConversationReply(
            conversationId = "conversation-1",
            body = SendConversationReplyRequest(
                content = emptyList(),
                externalID = "external-123"
            )
        )

        val request = mockServer.takeRequest()
        assertEquals("/engage/conversations/conversation-1/replies?deviceIdentifier=device-123&userID=user-123", request.path)
        assertEquals("Bearer id-token-123", request.getHeader("Authorization"))
        assertEquals("account-token", request.getHeader("x-rover-account-token"))
    }

    @Test
    fun `conversations request includes userID when sdk auth is not enabled for the domain`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        authenticationContext = FakeAuthenticationContext(
            sdkToken = "account-token",
            idToken = null,
            sdkAuthenticationEnabledDomains = emptySet()
        )
        httpClient = httpClient()

        httpClient.client.newCall(
            Request.Builder()
                .url(mockServer.url("/conversations"))
                .get()
                .build()
        ).execute().close()

        val request = mockServer.takeRequest()
        assertEquals("/conversations?deviceIdentifier=device-123&userID=user-123", request.path)
        assertNull(request.getHeader("Authorization"))
    }

    private fun httpClient(): EngageHttpClient {
        val mockContext = mock<Context>()
        val mockPackageManager = mock<android.content.pm.PackageManager>()
        val mockPackageInfo = mock<android.content.pm.PackageInfo>().apply {
            versionName = "1.0.0"
        }

        whenever(mockContext.packageName).thenReturn("io.rover.test")
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockPackageManager.getPackageInfo("io.rover.test", 0)).thenReturn(mockPackageInfo)

        return EngageHttpClient(
            context = mockContext,
            accountToken = "account-token",
            authenticationContext = authenticationContext,
            userInfo = mockUserInfo,
            deviceIdentification = mockDeviceIdentification,
        )
    }

    private class FakeAuthenticationContext(
        override val sdkToken: String?,
        private val idToken: String?,
        override val sdkAuthenticationEnabledDomains: Set<String>,
    ) : AuthenticationContextInterface {
        var idTokenLookupCount: Int = 0

        override var sdkAuthenticationIdTokenRefreshCallback: () -> Unit = {}

        override fun setSdkAuthenticationIdToken(token: String?) = Unit

        override fun clearSdkAuthenticationIdToken() = Unit

        override suspend fun obtainSdkAuthenticationIdToken(checkValidity: Boolean): String? {
            idTokenLookupCount += 1
            return idToken
        }

        override fun enableSdkAuthIdTokenRefreshForDomain(pattern: String) = Unit
    }
}
