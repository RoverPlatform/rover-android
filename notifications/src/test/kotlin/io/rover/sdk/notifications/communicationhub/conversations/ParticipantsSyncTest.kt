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
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantEntity
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantsSync
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import retrofit2.Response

class ParticipantsSyncTest : RoverEngageTestBase() {
    @Test
    fun syncMergesSnapshotAndPreservesRowsOmittedFromResponse() = runTest {
        val participantsSync = ParticipantsSync(
            engageApiService = mockEngageApiService,
            participantsDao = database!!.participantsDao(),
        )

        // The server can legitimately omit participants (e.g. broadcast-seed owners) from the
        // /participants snapshot; the local row must survive the sync.
        database!!.participantsDao().upsertParticipants(
            listOf(
                ParticipantEntity(
                    id = "omitted-participant",
                    name = "Omitted",
                    bio = null,
                    avatarUrl = "https://example.com/omitted.png",
                    updatedAt = 1703980800000L,
                ),
            )
        )

        doReturn(
            Response.success(
                """
                    {
                      "participants": [
                        { "id": "participant-1", "name": "Alex", "avatarURL": null, "updatedAt": "2024-01-01T00:00:00Z" },
                        { "id": "participant-2", "name": "Sam", "avatarURL": null, "updatedAt": "2024-01-02T00:00:00Z" }
                      ]
                    }
                """.trimIndent().toResponseBody(null)
            )
        ).whenever(mockEngageApiService).getParticipants()

        assertThat(participantsSync.sync(), equalTo(true))
        assertThat(
            database!!.participantsDao().getAllParticipants().map { it.id }.sorted(),
            equalTo(listOf("omitted-participant", "participant-1", "participant-2"))
        )
        val omitted = database!!.participantsDao().getParticipantById("omitted-participant")
        assertThat(omitted?.name, equalTo("Omitted"))
        assertThat(omitted?.avatarUrl, equalTo("https://example.com/omitted.png"))
    }

    @Test
    fun syncWithEmptyParticipantsResponsePreservesExistingRows() = runTest {
        val participantsSync = ParticipantsSync(
            engageApiService = mockEngageApiService,
            participantsDao = database!!.participantsDao(),
        )

        // Identity resolution misses server-side yield a 200 with an empty array; that must not
        // wipe good local rows.
        database!!.participantsDao().upsertParticipants(
            listOf(
                ParticipantEntity(
                    id = "participant-1",
                    name = "Alex",
                    bio = "Support agent",
                    avatarUrl = "https://example.com/alex.png",
                    updatedAt = 1703980800000L,
                ),
            )
        )

        doReturn(
            Response.success("""{ "participants": [] }""".toResponseBody(null))
        ).whenever(mockEngageApiService).getParticipants()

        assertThat(participantsSync.sync(), equalTo(true))
        val preserved = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(preserved?.name, equalTo("Alex"))
        assertThat(preserved?.bio, equalTo("Support agent"))
        assertThat(preserved?.avatarUrl, equalTo("https://example.com/alex.png"))
    }

    @Test
    fun syncWithNullFieldsDoesNotDegradeExistingRowButAppliesNonNullValues() = runTest {
        val participantsSync = ParticipantsSync(
            engageApiService = mockEngageApiService,
            participantsDao = database!!.participantsDao(),
        )

        database!!.participantsDao().upsertParticipants(
            listOf(
                ParticipantEntity(
                    id = "participant-1",
                    name = "Alex",
                    bio = null,
                    avatarUrl = "https://example.com/alex.png",
                    updatedAt = 1703980800000L,
                ),
            )
        )

        // Unresolved member profile: null name, but a fresh avatar. The null must not clobber
        // the good name; the non-null avatar must win.
        doReturn(
            Response.success(
                """
                    {
                      "participants": [
                        { "id": "participant-1", "name": null, "avatarURL": "https://example.com/alex-2.png", "updatedAt": "2024-01-02T00:00:00Z" }
                      ]
                    }
                """.trimIndent().toResponseBody(null)
            )
        ).whenever(mockEngageApiService).getParticipants()

        assertThat(participantsSync.sync(), equalTo(true))
        val merged = database!!.participantsDao().getParticipantById("participant-1")
        assertThat(merged?.name, equalTo("Alex"))
        assertThat(merged?.avatarUrl, equalTo("https://example.com/alex-2.png"))
    }

    @Test
    fun syncReturnsFalseWhenRequestFails() = runTest {
        val participantsSync = ParticipantsSync(
            engageApiService = mockEngageApiService,
            participantsDao = database!!.participantsDao(),
        )

        doReturn(Response.error<okhttp3.ResponseBody>(500, "boom".toResponseBody(null)))
            .whenever(mockEngageApiService).getParticipants()

        assertThat(participantsSync.sync(), equalTo(false))
        assertThat(database!!.participantsDao().getAllParticipants(), equalTo(emptyList()))
    }
}
