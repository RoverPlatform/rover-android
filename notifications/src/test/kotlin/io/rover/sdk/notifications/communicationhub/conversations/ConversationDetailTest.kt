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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollToIndex
import io.rover.sdk.notifications.communicationhub.messages.formatReplyClockTime





import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationDetailTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun participantInfoNotRenderedInComposableBodyWhenAvatarMissing() {
        // The participant header (name, initials placeholder, bio) moved to the app bar —
        // it is no longer rendered inside the ConversationDetail composable.
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        title = "Morgan Lee",
                        participantAvatarUrl = null,
                        participantBio = "Concert concierge",
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("Morgan Lee").assertDoesNotExist()
        assertEquals(0, composeRule.onAllNodesWithText("M").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Concert concierge").assertDoesNotExist()
    }

    @Test
    fun participantInfoNotRenderedInComposableBodyWhenAvatarPresent() {
        // The participant header (name, initials placeholder, bio) moved to the app bar —
        // it is no longer rendered inside the ConversationDetail composable.
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        title = "Morgan Lee",
                        participantAvatarUrl = "https://example.com/avatar.jpg",
                        participantBio = "   ",
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("Morgan Lee").assertDoesNotExist()
        assertEquals(0, composeRule.onAllNodesWithText("M").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("bio").assertDoesNotExist()
    }

    @Test
    fun spinnerVisibleWhenLoadingOlder() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        isLoadingOlder = true,
                        canLoadOlder = true,
                        replies = listOf(
                            testReply("1", "Hello"),
                        ),
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithTag("loading-older-spinner").assertExists()
    }

    @Test
    fun spinnerNotVisibleWhenNotLoadingOlder() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        isLoadingOlder = false,
                        canLoadOlder = true,
                        replies = listOf(
                            testReply("1", "Hello"),
                        ),
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithTag("loading-older-spinner").assertDoesNotExist()
    }

    @Test
    fun rendersThreadRowsWithGroupedMessageBlocksAndDayDivider() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        replies = emptyList(),
                        threadRows = listOf(
                            ConversationThreadRow.DayDivider(
                                dateLabel = "Today",
                                dayStartMillis = 1L,
                            ),
                            ConversationThreadRow.MessageGroup(
                                id = "group-1",
                                senderKey = "participant-1",
                                senderName = "Test User",
                                senderAvatarUrl = null,
                                isOutgoing = false,
                                replies = listOf(
                                    testReply("1", "First message"),
                                    testReply("2", "Second message"),
                                ),
                            ),
                        ),
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("Today").assertExists()
        composeRule.onNodeWithText("First message").assertExists()
        composeRule.onNodeWithText("Second message").assertExists()
        assertEquals(1, composeRule.onAllNodesWithText("Test User", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun rendersNotDeliveredIndicatorForFailedOutgoingReply() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        threadRows = listOf(
                            ConversationThreadRow.MessageGroup(
                                id = "group-1",
                                senderKey = null,
                                senderName = null,
                                senderAvatarUrl = null,
                                isOutgoing = true,
                                replies = listOf(
                                    testReply(
                                        id = "failed-1",
                                        text = "Please send this",
                                        isOutgoing = true,
                                        syncState = ReplyEntity.SYNC_STATE_FAILED,
                                    )
                                ),
                            )
                        ),
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("Please send this").assertIsDisplayed()
        composeRule.onNodeWithText("Not Delivered").assertIsDisplayed()
    }

    @Test
    fun doesNotRenderNotDeliveredIndicatorForQueuedOutgoingReply() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        threadRows = listOf(
                            ConversationThreadRow.MessageGroup(
                                id = "group-1",
                                senderKey = null,
                                senderName = null,
                                senderAvatarUrl = null,
                                isOutgoing = true,
                                replies = listOf(
                                    testReply(
                                        id = "queued-1",
                                        text = "Please send this",
                                        isOutgoing = true,
                                        syncState = ReplyEntity.SYNC_STATE_QUEUED,
                                    )
                                ),
                            )
                        ),
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("Please send this").assertIsDisplayed()
        composeRule.onNodeWithText("Not Delivered").assertDoesNotExist()
    }

    @Test
    fun rendersNotDeliveredForEarlierFailedReplyWhenLaterReplyInSameGroupSucceeds() {
        val sentAt = 1_704_067_200_000L // 2024-01-01T00:00:00Z
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        threadRows = listOf(
                            ConversationThreadRow.MessageGroup(
                                id = "group-1",
                                senderKey = null,
                                senderName = null,
                                senderAvatarUrl = null,
                                isOutgoing = true,
                                replies = listOf(
                                    testReply(
                                        id = "failed-1",
                                        text = "This one failed",
                                        isOutgoing = true,
                                        syncState = ReplyEntity.SYNC_STATE_FAILED,
                                    ),
                                    ConversationTestFixtures.replyRow(
                                        id = "sent-1",
                                        text = "This one sent",
                                        isOutgoing = true,
                                        sentAt = sentAt,
                                        syncState = ReplyEntity.SYNC_STATE_CONFIRMED,
                                    ),
                                ),
                            )
                        ),
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        // The earlier failed reply is still marked, even though a later reply in the
        // same group succeeded and the group shows a normal timestamp under its last bubble.
        composeRule.onNodeWithText("Not Delivered").assertIsDisplayed()
        composeRule.onNodeWithText(formatReplyClockTime(java.util.Date(sentAt))).assertIsDisplayed()
    }

    @Test
    fun rendersSendingIndicatorForQueuedOutgoingReply() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        threadRows = listOf(
                            ConversationThreadRow.MessageGroup(
                                id = "group-1",
                                senderKey = null,
                                senderName = null,
                                senderAvatarUrl = null,
                                isOutgoing = true,
                                replies = listOf(
                                    testReply(
                                        id = "queued-1",
                                        text = "Please send this",
                                        isOutgoing = true,
                                        syncState = ReplyEntity.SYNC_STATE_QUEUED,
                                    )
                                ),
                            )
                        ),
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("Sending…").assertIsDisplayed()
    }

    @Test
    fun rendersTimestampForMostRecentGroupWithoutQueuedReply() {
        val sentAt = 1_704_067_200_000L // 2024-01-01T00:00:00Z
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        threadRows = listOf(
                            ConversationThreadRow.MessageGroup(
                                id = "group-1",
                                senderKey = null,
                                senderName = null,
                                senderAvatarUrl = null,
                                isOutgoing = true,
                                replies = listOf(
                                    ConversationTestFixtures.replyRow(
                                        id = "sent-1",
                                        text = "Delivered already",
                                        isOutgoing = true,
                                        sentAt = sentAt,
                                        syncState = ReplyEntity.SYNC_STATE_CONFIRMED,
                                    )
                                ),
                            )
                        ),
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("Sending…").assertDoesNotExist()
        composeRule.onNodeWithText(formatReplyClockTime(java.util.Date(sentAt))).assertIsDisplayed()
    }

    @Test
    fun loadOlderButtonNoLongerAppears() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(
                        canLoadOlder = true,
                        replies = listOf(
                            testReply("1", "Hello"),
                        ),
                    ),
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("Load older messages").assertDoesNotExist()
    }

    @Test
    fun loadOlderRequestsAfterStateChangesAndUserScrollsNearTop() {
        val loadOlderCount = AtomicInteger(0)
        val uiState = mutableStateOf(
            baseUiState.copy(
                isLoading = false,
                canLoadOlder = false,
                threadRows = buildThreadRows(30),
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = uiState.value,
                    onComposerTextChanged = {},
                    onLoadOlderRequested = { loadOlderCount.incrementAndGet() },
                    onSendTapped = {},
                )
            }
        }

        composeRule.runOnIdle {
            uiState.value = uiState.value.copy(canLoadOlder = true)
        }

        composeRule.onNode(hasScrollAction()).performScrollToIndex(29)

        composeRule.waitUntil(5_000) {
            loadOlderCount.get() == 1
        }

        assertEquals(1, loadOlderCount.get())
    }

    @Test
    fun autoScrollsWhenNewestGroupGrowsWithoutChangingRowCount() {
        val uiState = mutableStateOf(
            baseUiState.copy(
                isLoading = false,
                threadRows = buildRowsForAutoScrollTest(
                    newestReplies = listOf("Newest reply"),
                ),
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = uiState.value,
                    onComposerTextChanged = {},
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToIndex(1)

        composeRule.runOnIdle {
            uiState.value = uiState.value.copy(
                threadRows = buildRowsForAutoScrollTest(
                    newestReplies = listOf("Newest reply", "Appended reply"),
                ),
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Appended reply").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Appended reply").assertIsDisplayed()
    }

    private companion object {
        val baseUiState = ConversationDetailUiState(
            isLoading = false,
            canLoadOlder = false,
            composerText = "",
        )

        fun testReply(
            id: String,
            text: String,
            isOutgoing: Boolean = false,
            syncState: String = ReplyEntity.SYNC_STATE_CONFIRMED,
        ) = ConversationTestFixtures.replyRow(
            id = id,
            text = text,
            isOutgoing = isOutgoing,
            syncState = syncState,
        )

        fun buildReplies(count: Int): List<ConversationReplyRow> = (1..count).map { index ->
            testReply(
                id = index.toString().padStart(3, '0'),
                text = "Backfill reply 100 #${index.toString().padStart(3, '0')}",
            )
        }

        fun buildThreadRows(count: Int): List<ConversationThreadRow.MessageGroup> = (1..count).map { index ->
            ConversationThreadRow.MessageGroup(
                id = index.toString().padStart(3, '0'),
                senderKey = "participant-1",
                senderName = "Test User",
                senderAvatarUrl = null,
                isOutgoing = false,
                replies = listOf(
                    testReply(
                        id = index.toString().padStart(3, '0'),
                        text = "Backfill reply 100 #${index.toString().padStart(3, '0')}",
                    ),
                ),
            )
        }

        fun buildRowsForAutoScrollTest(newestReplies: List<String>): List<ConversationThreadRow> {
            val olderGroup = ConversationThreadRow.MessageGroup(
                id = "group-older",
                senderKey = "participant-1",
                senderName = "Test User",
                senderAvatarUrl = null,
                isOutgoing = false,
                replies = listOf(testReply("older", "Older reply")),
            )
            val newestGroup = ConversationThreadRow.MessageGroup(
                id = "group-newest",
                senderKey = "participant-1",
                senderName = "Test User",
                senderAvatarUrl = null,
                isOutgoing = false,
                replies = newestReplies.mapIndexed { index, text ->
                    testReply(id = "newest-${index + 1}", text = text)
                },
            )

            return listOf(
                ConversationThreadRow.DayDivider(dateLabel = "Today", dayStartMillis = 1L),
                olderGroup,
                newestGroup,
            )
        }
    }
}
