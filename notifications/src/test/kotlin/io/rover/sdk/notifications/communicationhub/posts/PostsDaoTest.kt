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
import io.rover.sdk.notifications.communicationhub.posts.PostEntity
import io.rover.sdk.notifications.communicationhub.posts.SubscriptionEntity
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class PostsDaoTest : RoverEngageTestBase() {

    @Test
    fun postsOrderedByReceivedAtDesc() = runBlocking {
        val dao = database!!.postsDao()
        val older = PostEntity("p1", "S1", "P1", 1L, null, false, null, null)
        val newer = PostEntity("p2", "S2", "P2", 2L, null, true, null, null)
        dao.upsertPosts(listOf(older, newer))

        val list = dao.getAllPosts().first()
        assertThat(list.map { it.id }, equalTo(listOf("p2", "p1")))
    }

    @Test
    fun observingAPostByIdEmitsNullUntilItArrivesThenTheRow() = runBlocking {
        val dao = database!!.postsDao()

        // One continuous subscription, started before the row exists, so the test proves Room
        // re-emits into an existing collector (what a screen opened before a sync relies on),
        // not merely that a fresh query after the insert finds the row. The collector itself
        // signals its first emission, so the insert cannot race ahead of it.
        val firstEmission = CompletableDeferred<Unit>()
        val emissions = async {
            dao.observePostWithSubscriptionById("p1")
                .onEach { firstEmission.complete(Unit) }
                .take(2)
                .toList()
        }
        withTimeout(5_000) { firstEmission.await() }

        val p = PostEntity("p1", "S", "P", 1L, "https://example.com", false, null, null)
        dao.upsertPost(p)

        val (before, after) = withTimeout(5_000) { emissions.await() }
        assertThat(before, equalTo(null as PostWithSubscription?))
        assertThat(after?.post, equalTo(p))
    }

    @Test
    fun postsGetPostByIdRoundTrip() = runBlocking {
        val dao = database!!.postsDao()
        val p = PostEntity("p1", "S", "P", 1L, "https://example.com", false, null, null)
        dao.upsertPost(p)
        val fetched = dao.getPostById("p1")
        assertThat(fetched, equalTo(p))
    }

    @Test
    fun postsMarkAsReadUpdatesReadState() = runBlocking {
        val dao = database!!.postsDao()
        val p1 = PostEntity("p1", "S1", "P1", 1L, null, false, null, null)
        val p2 = PostEntity("p2", "S2", "P2", 2L, null, true, null, null)
        dao.upsertPosts(listOf(p1, p2))

        assertThat(dao.getPostById("p1")?.isRead, equalTo(false))
        dao.markPostAsRead("p1")
        assertThat(dao.getPostById("p1")?.isRead, equalTo(true))
        // untouched posts keep their state
        assertThat(dao.getPostById("p2")?.isRead, equalTo(true))
    }

    @Test
    fun postsWithSubscriptionsJoinAndOrdering() = runBlocking {
        val subs = database!!.subscriptionsDao()
        val posts = database!!.postsDao()
        subs.upsertSubscription(SubscriptionEntity("s1", "Name", "Desc", true, "active", "https://example.com/sub.png"))
        posts.upsertPosts(
            listOf(
                PostEntity("p1", "S1", "P1", 1L, null, false, null, "s1"),
                PostEntity("p2", "S2", "P2", 2L, null, true, null, "s1")
            )
        )

        val joined = posts.getAllPostsWithSubscriptions().first()
        assertThat(joined.map { it.post.id }, equalTo(listOf("p2", "p1")))
        val map = joined.associateBy { it.post.id }
        assertThat(map["p1"]?.subscription?.id, equalTo("s1"))
        assertThat(map["p2"]?.subscription?.id, equalTo("s1"))
        assertThat(map["p1"]?.subscription?.logoURL, equalTo("https://example.com/sub.png"))
    }

    @Test
    fun postsDeletePostRemovesFromDatabase() = runBlocking {
        val dao = database!!.postsDao()
        val p1 = PostEntity("p1", "S1", "P1", 1L, null, false, null, null)
        val p2 = PostEntity("p2", "S2", "P2", 2L, null, false, null, null)
        dao.upsertPosts(listOf(p1, p2))

        assertThat(dao.getAllPosts().first().size, equalTo(2))
        dao.deletePost("p1")
        val remaining = dao.getAllPosts().first()
        assertThat(remaining.size, equalTo(1))
        assertThat(remaining.first().id, equalTo("p2"))
    }

    @Test
    fun postsUpsertUpdatesExistingPost() = runBlocking {
        val dao = database!!.postsDao()
        val original = PostEntity("p1", "Original", "Preview", 1L, null, false, null, null)
        dao.upsertPost(original)

        val updated = PostEntity("p1", "Updated", "New Preview", 1L, null, true, null, null)
        dao.upsertPost(updated)

        val fetched = dao.getPostById("p1")
        assertThat(fetched?.subject, equalTo("Updated"))
        assertThat(fetched?.isRead, equalTo(true))
    }
}
