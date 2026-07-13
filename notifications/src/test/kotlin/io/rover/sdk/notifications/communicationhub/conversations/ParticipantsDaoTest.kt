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

package io.rover.sdk.notifications.communicationhub.conversations

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ParticipantsDaoTest : RoverEngageTestBase() {

    @Test
    fun participantsUpsertByIdIsIdempotent() = runBlocking {
        val dao = database!!.participantsDao()
        val original = ConversationTestFixtures.participant(id = "participant-1", name = "Ada Lovelace")
        val updated = original.copy(avatarUrl = "https://example.com/avatar.png")

        dao.upsertParticipant(original)
        dao.upsertParticipant(updated)

        assertThat(dao.getAllParticipants().size, equalTo(1))
        assertThat(dao.getParticipantById("participant-1")?.avatarUrl, equalTo("https://example.com/avatar.png"))
    }

    @Test
    fun mergePreservesExistingFieldsWhenIncomingFieldsAreNull() = runBlocking {
        val dao = database!!.participantsDao()
        dao.upsertParticipant(
            ConversationTestFixtures.participant(
                id = "participant-1",
                name = "Ada Lovelace",
                avatarUrl = "https://example.com/ada.png",
                bio = "Mathematician",
            )
        )

        // A degraded incoming row (null name/bio/avatar) must not null out good local values.
        dao.mergeParticipants(
            listOf(
                ConversationTestFixtures.participant(
                    id = "participant-1",
                    name = null,
                    avatarUrl = null,
                    bio = null,
                    updatedAt = ConversationTestFixtures.T1,
                )
            )
        )

        val merged = dao.getParticipantById("participant-1")
        assertThat(merged?.name, equalTo("Ada Lovelace"))
        assertThat(merged?.avatarUrl, equalTo("https://example.com/ada.png"))
        assertThat(merged?.bio, equalTo("Mathematician"))
        assertThat(merged?.updatedAt, equalTo(ConversationTestFixtures.T1))
    }

    @Test
    fun mergeAppliesNonNullIncomingValues() = runBlocking {
        val dao = database!!.participantsDao()
        dao.upsertParticipant(
            ConversationTestFixtures.participant(
                id = "participant-1",
                name = "Ada Lovelace",
                avatarUrl = "https://example.com/ada.png",
            )
        )

        dao.mergeParticipant(
            ConversationTestFixtures.participant(
                id = "participant-1",
                name = "Ada King",
                avatarUrl = "https://example.com/ada-2.png",
                updatedAt = ConversationTestFixtures.T2,
            )
        )

        val merged = dao.getParticipantById("participant-1")
        assertThat(merged?.name, equalTo("Ada King"))
        assertThat(merged?.avatarUrl, equalTo("https://example.com/ada-2.png"))
    }

    @Test
    fun mergeInsertsRowsThatDoNotExistYet() = runBlocking {
        val dao = database!!.participantsDao()

        dao.mergeParticipants(
            listOf(
                ConversationTestFixtures.participant(id = "participant-1", name = "Ada Lovelace"),
                ConversationTestFixtures.participant(id = "participant-2", name = null),
            )
        )

        assertThat(dao.getAllParticipants().map { it.id }.sorted(), equalTo(listOf("participant-1", "participant-2")))
        assertThat(dao.getParticipantById("participant-2")?.name, equalTo(null as String?))
    }
}
