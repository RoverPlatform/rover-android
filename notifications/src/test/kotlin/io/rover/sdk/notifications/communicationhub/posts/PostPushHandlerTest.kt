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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The handler's return value is what [io.rover.sdk.notifications.PushReceiver] reports to the host
 * for a post push, so it is pinned here against the real decoder rather than only against a mock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PostPushHandlerTest {

    @Test
    fun anAcceptedPostPushReportsHandledAndSaves() = runTest {
        val repository = mock<PostsRepository>()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val handler = PostPushHandler(repository = repository, coroutineScope = testScope)

        val handled = handler.handleCommunicationHubPush(
            """
                {
                  "post": {
                    "id": "post-1",
                    "subject": "Subject",
                    "isRead": false,
                    "previewText": "Preview",
                    "receivedAt": "2024-01-01T00:00:00Z",
                    "url": null,
                    "coverImageURL": null,
                    "subscriptionID": null
                  }
                }
            """.trimIndent()
        )

        testScope.advanceUntilIdle()

        assertThat(handled, equalTo(true))
        verify(repository).savePostFromPush(any())
    }

    @Test
    fun aPostPushWithNoPostObjectReportsUnhandled() = runTest {
        val repository = mock<PostsRepository>()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val handler = PostPushHandler(repository = repository, coroutineScope = testScope)

        val handled = handler.handleCommunicationHubPush("""{"notification":{}}""")
        testScope.advanceUntilIdle()

        assertThat(handled, equalTo(false))
        verify(repository, never()).savePostFromPush(any())
    }

    @Test
    fun aMalformedPostPushReportsUnhandled() = runTest {
        val repository = mock<PostsRepository>()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val handler = PostPushHandler(repository = repository, coroutineScope = testScope)

        val handled = handler.handleCommunicationHubPush("""{"post":{"id":"post-1"}}""")
        testScope.advanceUntilIdle()

        assertThat(handled, equalTo(false))
        verify(repository, never()).savePostFromPush(any())
    }
}
