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

package io.rover.sdk.notifications.communicationhub.data.database.dao

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.data.database.entities.SubscriptionEntity
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SubscriptionsDaoTest : RoverEngageTestBase() {

    @Test
    fun subscriptionsDaoUpsertAndClear() = runBlocking {
        val subs = database!!.subscriptionsDao()
        subs.upsertSubscription(SubscriptionEntity("s1", "N", "D", true, "active"))
        assertThat(subs.getSubscriptionById("s1")?.name, equalTo("N"))

        subs.clearAllSubscriptions()
        assertThat(subs.getSubscriptionById("s1"), equalTo(null))
    }

    @Test
    fun subscriptionsGetAllReturnsAllSubscriptions() = runBlocking {
        val subs = database!!.subscriptionsDao()
        val sub1 = SubscriptionEntity("s1", "Sub 1", "Desc 1", true, "active")
        val sub2 = SubscriptionEntity("s2", "Sub 2", "Desc 2", false, "archived")
        subs.upsertSubscriptions(listOf(sub1, sub2))

        val all = subs.getAllSubscriptions()
        assertThat(all.size, equalTo(2))
        assertThat(all.map { it.id }, equalTo(listOf("s1", "s2")))
    }

    @Test
    fun subscriptionsUpsertUpdatesExisting() = runBlocking {
        val subs = database!!.subscriptionsDao()
        val original = SubscriptionEntity("s1", "Original", "Desc", false, "active")
        subs.upsertSubscription(original)

        val updated = SubscriptionEntity("s1", "Updated", "New Desc", true, "archived")
        subs.upsertSubscription(updated)

        val fetched = subs.getSubscriptionById("s1")
        assertThat(fetched?.name, equalTo("Updated"))
        assertThat(fetched?.optIn, equalTo(true))
        assertThat(fetched?.status, equalTo("archived"))
    }

    @Test
    fun subscriptionsGetByIdReturnsNullForNonExistent() = runBlocking {
        val subs = database!!.subscriptionsDao()
        val fetched = subs.getSubscriptionById("non-existent")
        assertThat(fetched, equalTo(null))
    }
}