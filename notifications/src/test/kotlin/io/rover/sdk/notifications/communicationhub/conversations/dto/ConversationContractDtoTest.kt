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

package io.rover.sdk.notifications.communicationhub.conversations.dto

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversationContractDtoTest {
    private val moshi = Moshi.Builder().build()

    @Test
    fun conversationItemParsesSpecFieldsAndMapsToEntity() {
        val adapter = moshi.adapter(ConversationItem::class.java)
        val item = adapter.fromJson(
            """
            {
              "id": "conversation-1",
              "subject": null,
              "lastReplyAt": "2024-01-04T00:00:00Z",
              "lastIncomingReplyAt": "2024-01-03T00:00:00Z",
              "lastReadAt": "2024-01-02T00:00:00Z",
              "lastReadReplyID": "reply-2",
              "lastReplyPreview": "Latest reply",
              "createdAt": "2024-01-01T00:00:00Z",
              "updatedAt": "2024-01-05T00:00:00Z",
              "participantIDs": ["participant-1", "participant-2"]
            }
            """.trimIndent()
        )!!

        val entity = item.toEntity()

        assertThat(entity.subject, equalTo(null as String?))
        assertThat(entity.lastReplyAt, equalTo(1704326400000L))
        assertThat(entity.lastIncomingReplyAt, equalTo(1704240000000L))
        assertThat(entity.lastReadAt, equalTo(1704153600000L))
        assertThat(entity.lastReadReplyID, equalTo("reply-2"))
        assertThat(entity.lastReplyPreview, equalTo("Latest reply"))
        assertThat(entity.createdAt, equalTo(1704067200000L))
        assertThat(entity.updatedAt, equalTo(1704412800000L))
        assertThat(entity.participantIDs, equalTo(listOf("participant-1", "participant-2")))
    }

    @Test
    fun conversationItemPreservesOmittedParticipantIDsAsAbsent() {
        val adapter = moshi.adapter(ConversationItem::class.java)
        val item = adapter.fromJson(
            """
            {
              "id": "conversation-2",
              "subject": null,
              "lastReplyAt": "2024-01-04T00:00:00Z",
              "lastIncomingReplyAt": "2024-01-03T00:00:00Z",
              "lastReadAt": "2024-01-02T00:00:00Z",
              "lastReadReplyID": "reply-2",
              "lastReplyPreview": "Latest reply",
              "createdAt": "2024-01-01T00:00:00Z",
              "updatedAt": "2024-01-05T00:00:00Z"
            }
            """.trimIndent()
        )!!

        val entity = item.toEntity()

        assertThat(item.participantIDs, equalTo(null as List<String>?))
        assertThat(entity.participantIDs, equalTo(null as List<String>?))
    }

    @Test
    fun replyItemParsesSpecFieldsAndContentUsesExactVocabulary() {
        val adapter = moshi.adapter(ReplyItem::class.java)
        val item = adapter.fromJson(
            """
            {
              "id": "reply-1",
              "conversationID": "conversation-1",
              "senderType": "participant",
              "participantID": "participant-1",
              "content": [
                {
                  "type": "text",
                  "text": "Hello world"
                },
                {
                  "type": "image",
                  "url": "https://example.com/image.png"
                }
              ],
              "externalID": "external-1",
              "createdAt": "2024-01-06T00:00:00Z"
            }
            """.trimIndent()
        )!!

        val entity = item.toEntity()

        assertThat(entity.conversationID, equalTo("conversation-1"))
        assertThat(entity.senderType, equalTo("participant"))
        assertThat(entity.participantID, equalTo("participant-1"))
        assertThat(entity.externalID, equalTo("external-1"))
        assertThat(entity.createdAt, equalTo(1704499200000L))
        assertThat(entity.content, equalTo(
            listOf(
                io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock(
                    type = io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock.TYPE_TEXT,
                    text = "Hello world",
                    url = null,
                ),
                io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock(
                    type = io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock.TYPE_IMAGE,
                    text = null,
                    url = "https://example.com/image.png",
                ),
            )
        ))
    }

    @Test
    fun replyContentBlockItemPassesThroughUnknownType() {
        val adapter = moshi.adapter(ReplyItem::class.java)
        val item = adapter.fromJson(
            """
            {
              "id": "reply-2",
              "conversationID": "conversation-1",
              "senderType": "participant",
              "participantID": "participant-1",
              "content": [
                {
                  "type": "video",
                  "url": "https://example.com/video.mp4"
                }
              ],
              "externalID": null,
              "createdAt": "2024-01-06T00:00:00Z"
            }
            """.trimIndent()
        )!!

        val entity = item.toEntity()

        assertThat(entity.content.size, equalTo(1))
        assertThat(entity.content[0].type, equalTo("video"))
        assertThat(entity.content[0].url, equalTo("https://example.com/video.mp4"))
        assertThat(entity.content[0].text, equalTo(null as String?))
    }

    @Test
    fun conversationSyncPageParsesIncludedParticipantsAndExactNextBefore() {
        val adapter = moshi.adapter(ConversationSyncPage::class.java).failOnUnknown()
        val page = adapter.fromJson(
            """
            {
              "conversations": [
                {
                  "id": "conversation-1",
                  "subject": null,
                  "lastReplyAt": "2024-01-04T00:00:00Z",
                  "lastIncomingReplyAt": "2024-01-03T00:00:00Z",
                  "lastReadAt": "2024-01-02T00:00:00Z",
                  "lastReadReplyID": "reply-2",
                  "lastReplyPreview": "Latest reply",
                  "createdAt": "2024-01-01T00:00:00Z",
                  "updatedAt": "2024-01-05T00:00:00Z",
                  "participantIDs": ["participant-1"]
                }
              ],
              "included": {
                "participants": [
                  {
                    "id": "participant-1",
                    "name": "Morgan Lee",
                    "avatarURL": null,
                    "updatedAt": "2024-01-01T00:00:00Z"
                  }
                ]
              },
              "nextCursor": "forward-1",
              "nextBefore": "back-1",
              "hasMore": true
            }
            """.trimIndent()
        )!!

        assertThat(page.conversations.map { it.id }, equalTo(listOf("conversation-1")))
        assertThat(page.included?.participants?.map { it.id }, equalTo(listOf("participant-1")))
        assertThat(page.nextCursor, equalTo("forward-1"))
        assertThat(page.nextBefore, equalTo("back-1"))
        assertThat(page.backwardCursor, equalTo("back-1"))
    }

    @Test
    fun conversationSyncPageDoesNotFallbackToBefore() {
        val adapter = moshi.adapter(ConversationSyncPage::class.java).failOnUnknown()

        assertThrows(JsonDataException::class.java) {
            adapter.fromJson(
                """
                {
                  "conversations": [],
                  "included": { "participants": [] },
                  "nextCursor": null,
                  "before": "legacy-back-1",
                  "hasMore": true
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun repliesSyncPageParsesExactEnvelopeWithoutTopLevelParticipants() {
        val adapter = moshi.adapter(RepliesSyncPage::class.java).failOnUnknown()
        val page = adapter.fromJson(
            """
            {
              "replies": [
                {
                  "id": "reply-1",
                  "conversationID": "conversation-1",
                  "senderType": "participant",
                  "participantID": "participant-1",
                  "content": [{"type": "text", "text": "Hello"}],
                  "externalID": null,
                  "createdAt": "2024-01-06T00:00:00Z"
                }
              ],
              "nextCursor": null,
              "nextBefore": "back-1",
              "hasMore": true
            }
            """.trimIndent()
        )!!

        assertThat(page.replies.map { it.id }, equalTo(listOf("reply-1")))
        assertThat(page.nextBefore, equalTo("back-1"))
        assertThat(page.backwardCursor, equalTo("back-1"))
    }

    @Test
    fun repliesSyncPageDoesNotFallbackToBefore() {
        val adapter = moshi.adapter(RepliesSyncPage::class.java).failOnUnknown()

        assertThrows(JsonDataException::class.java) {
            adapter.fromJson(
                """
                {
                  "replies": [],
                  "nextCursor": null,
                  "before": "legacy-back-1",
                  "hasMore": true
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun conversationSyncPageRejectsCreatedAtFallbackForUpdatedAt() {
        val adapter = moshi.adapter(ConversationSyncPage::class.java).failOnUnknown()

        assertThrows(JsonDataException::class.java) {
            adapter.fromJson(
                """
                {
                  "conversations": [
                    {
                      "id": "conversation-1",
                      "subject": null,
                      "lastReplyAt": "2024-01-04T00:00:00Z",
                      "lastIncomingReplyAt": null,
                      "lastReadAt": null,
                      "lastReadReplyID": null,
                      "lastReplyPreview": null,
                      "createdAt": "2024-01-01T00:00:00Z",
                      "participantIDs": []
                    }
                  ],
                  "included": { "participants": [] },
                  "nextCursor": null,
                  "nextBefore": null,
                  "hasMore": false
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun repliesSyncPageRejectsCreatedAtAndBlocksFallbacks() {
        val adapter = moshi.adapter(RepliesSyncPage::class.java).failOnUnknown()

        assertThrows(JsonDataException::class.java) {
            adapter.fromJson(
                """
                {
                  "replies": [
                    {
                      "id": "reply-1",
                      "conversationID": "conversation-1",
                      "senderType": "participant",
                      "participantID": "participant-1",
                      "blocks": [{"type": "text", "text": "Hello"}],
                      "externalID": null,
                      "createdAt": "2024-01-06T00:00:00Z"
                    }
                  ],
                  "nextCursor": null,
                  "nextBefore": null,
                  "hasMore": false
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun repliesSyncPageRejectsUnknownTopLevelParticipantsField() {
        val adapter = moshi.adapter(RepliesSyncPage::class.java).failOnUnknown()

        assertThrows(JsonDataException::class.java) {
            adapter.fromJson(
                """
                {
                  "replies": [],
                  "participants": [],
                  "nextCursor": null,
                  "nextBefore": null,
                  "hasMore": false
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun conversationSyncPageRejectsMissingIncludedWhenParticipantsWereRequested() {
        val adapter = moshi.adapter(ConversationSyncPage::class.java).failOnUnknown()

        assertThrows(JsonDataException::class.java) {
            adapter.fromJson(
                """
                {
                  "conversations": [],
                  "nextCursor": null,
                  "nextBefore": null,
                  "hasMore": false
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun repliesSyncPageRejectsMissingRequiredEnvelopeFields() {
        val adapter = moshi.adapter(RepliesSyncPage::class.java).failOnUnknown()

        assertThrows(JsonDataException::class.java) {
            adapter.fromJson(
                """
                {
                  "replies": []
                }
                """.trimIndent()
            )
        }
    }

}
