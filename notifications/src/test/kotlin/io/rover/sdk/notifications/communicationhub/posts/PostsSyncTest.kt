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
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.reset

class PostsSyncTest : RoverEngageTestBase() {
    @Test
    fun testSyncTaskCancellationHandling() = runTest {
        configureMockForSuccess()

        val syncTask = async { postsSync.sync() }
        syncTask.cancelAndJoin()

        assertThat(syncTask.isCancelled, equalTo(true))

        reset(mockEngageApiService)

        val testPosts = createTestPosts(1, subscriptionID = "subscription-1")
        configureMockForSuccess(posts = testPosts)

        val newSyncTask = async { postsSync.sync() }
        assertThat(newSyncTask.await(), equalTo(true))
    }

    @Test
    fun testSyncCompletionClearsActiveSyncTask() = runTest {
        setupBasicMocks()
        assertThat(postsSync.sync(), equalTo(true))

        val testPosts = createTestPosts(1, subscriptionID = "subscription-1")
        configureMockForSuccess(posts = testPosts)
        assertThat(postsSync.sync(), equalTo(true))

        configureMockForFailure()
        assertThat(postsSync.sync(), equalTo(false))

        val recoveryPosts = createTestPosts(1, subscriptionID = "subscription-1")
        configureMockForSuccess(posts = recoveryPosts)
        assertThat(postsSync.sync(), equalTo(true))
    }
}
