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
import io.rover.sdk.notifications.communicationhub.posts.dto.SubscriptionItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SubscriptionsSyncTest : RoverEngageTestBase() {
    @Test
    fun successfulSyncPersistsSubscriptions() = runTest {
        val subscriptions = listOf(
            SubscriptionItem(
                id = "sub-1",
                name = "Sub 1",
                description = "First",
                optIn = true,
                status = "published",
                logoURL = "https://example.com/sub-1.png",
            ),
            SubscriptionItem(
                id = "sub-2",
                name = "Sub 2",
                description = "Second",
                optIn = false,
                status = "archived",
                logoURL = null,
            ),
        )
        configureMockForSuccess(subscriptions = subscriptions)

        assertThat(subscriptionsSync.sync(), equalTo(true))
        assertThat(database!!.subscriptionsDao().getAllSubscriptions().size, equalTo(2))
        assertThat(database!!.subscriptionsDao().getSubscriptionById("sub-1")?.logoURL, equalTo("https://example.com/sub-1.png"))
        assertThat(database!!.subscriptionsDao().getSubscriptionById("sub-2")?.logoURL, equalTo(null))
    }

    @Test
    fun failedSyncReturnsFalse() = runTest {
        configureMockForFailure()

        assertThat(subscriptionsSync.sync(), equalTo(false))
        assertThat(database!!.subscriptionsDao().getAllSubscriptions(), equalTo(emptyList()))
    }
}
