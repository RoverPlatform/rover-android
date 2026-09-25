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

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import io.rover.sdk.notifications.communicationhub.messages.formatReplyClockTime





import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationDetailTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        // Day-divider placement in buildConversationThreadRows depends on the default time
        // zone; pin it so the day-boundary tests are deterministic.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                    composerState = rememberTextFieldState(),
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
                canLoadOlder = false,
                threadRows = buildThreadRows(30),
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = uiState.value,
                    composerState = rememberTextFieldState(),
                    onLoadOlderRequested = { loadOlderCount.incrementAndGet() },
                    onSendTapped = {},
                )
            }
        }

        composeRule.runOnIdle {
            uiState.value = uiState.value.copy(canLoadOlder = true)
        }

        composeRule.onNodeWithTag("conversation-thread-list").performScrollToIndex(29)

        composeRule.waitUntil(5_000) {
            loadOlderCount.get() == 1
        }

        assertEquals(1, loadOlderCount.get())
    }

    @Test
    fun autoScrollsWhenNewestGroupGrowsWithoutChangingRowCount() {
        val uiState = mutableStateOf(
            baseUiState.copy(
                replies = buildRepliesForAutoScrollTest(
                    newestReplies = listOf("Newest reply"),
                ),
                threadRows = buildRowsForAutoScrollTest(
                    newestReplies = listOf("Newest reply"),
                ),
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = uiState.value,
                    composerState = rememberTextFieldState(),
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithTag("conversation-thread-list").performScrollToIndex(1)

        composeRule.runOnIdle {
            uiState.value = uiState.value.copy(
                replies = buildRepliesForAutoScrollTest(
                    newestReplies = listOf("Newest reply", "Appended reply"),
                ),
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

    @Test
    fun readMarkingFollowsRealScrollingAndSurvivesRecreation() {
        val history = viewportFillingHistory(dayStart = ConversationTestFixtures.T0)
        val arrivingReply = ConversationTestFixtures.replyRow(
            id = "history-31",
            text = "History reply #31",
            isOutgoing = false,
            sentAt = history.last().sentAt + THREE_MINUTES_MILLIS,
        )
        val uiState = mutableStateOf(
            baseUiState.copy(
                replies = history,
                threadRows = buildConversationThreadRows(history),
            )
        )
        val marked = mutableListOf<String>()

        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = uiState.value,
                    composerState = rememberTextFieldState(),
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                    onLatestReplyVisible = { replyId -> marked += replyId },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(listOf("history-30"), marked)

        // A real drag up into history flips the bottom-proximity gate.
        composeRule.onNodeWithTag("conversation-thread-list").performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            uiState.value = uiState.value.copy(
                replies = history + arrivingReply,
                threadRows = buildConversationThreadRows(history + arrivingReply),
            )
        }
        composeRule.waitForIdle()
        assertEquals(listOf("history-30"), marked)

        // Recreation restores the scrolled-up position and must not reset the gate to
        // "at the bottom".
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertEquals(listOf("history-30"), marked)

        // Returning to the bottom is what marks the deferred reply.
        var swipes = 0
        while (!marked.contains("history-31") && swipes < 10) {
            composeRule.onNodeWithTag("conversation-thread-list").performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            swipes++
        }
        assertEquals(listOf("history-30", "history-31"), marked)
    }

    @Test
    fun scrollsToRevealOutgoingReplyThatStartsANewDay() {
        // The repro shape: the newest message is from an earlier day, so the send inserts TWO
        // rows — a DayDivider plus a new MessageGroup — and the previously-newest row shifts
        // from index 0 to index 2 (the key-anchored scroll position follows it off-screen).
        val history = viewportFillingHistory(dayStart = ConversationTestFixtures.T0)
        val uiState = mutableStateOf(
            baseUiState.copy(
                replies = history,
                threadRows = buildConversationThreadRows(history),
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = uiState.value,
                    composerState = rememberTextFieldState(),
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.runOnIdle {
            val withSend = history + outgoingSend(sentAt = ConversationTestFixtures.T1 + TWELVE_HOURS_MILLIS)
            uiState.value = uiState.value.copy(
                replies = withSend,
                threadRows = buildConversationThreadRows(withSend),
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Fresh outgoing reply").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Fresh outgoing reply").assertIsDisplayed()
    }

    @Test
    fun scrollsToRevealOutgoingReplySentOnTheSameDay() {
        // Same-day send: only one new row (a new MessageGroup, no divider).
        val history = viewportFillingHistory(dayStart = ConversationTestFixtures.T0)
        val uiState = mutableStateOf(
            baseUiState.copy(
                replies = history,
                threadRows = buildConversationThreadRows(history),
            )
        )

        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = uiState.value,
                    composerState = rememberTextFieldState(),
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.runOnIdle {
            val withSend = history + outgoingSend(sentAt = ConversationTestFixtures.T0 + SIXTEEN_HOURS_MILLIS)
            uiState.value = uiState.value.copy(
                replies = withSend,
                threadRows = buildConversationThreadRows(withSend),
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Fresh outgoing reply").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Fresh outgoing reply").assertIsDisplayed()
    }

    @Test
    fun notFoundPhaseReportsTheMissingConversationAndOffersNoComposer() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(phase = ConversationDetailPhase.NotFound),
                    composerState = rememberTextFieldState(),
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("This conversation could not be found.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Write a reply").assertCountEquals(0)
    }

    @Test
    fun loadFailedPhaseSaysSoWithoutClaimingTheConversationIsMissing() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(phase = ConversationDetailPhase.LoadFailed),
                    composerState = rememberTextFieldState(),
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("This conversation could not be loaded.").assertIsDisplayed()
        composeRule.onAllNodesWithText("This conversation could not be found.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Write a reply").assertCountEquals(0)
    }

    @Test
    fun loadingPhaseOffersNoComposer() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(phase = ConversationDetailPhase.Loading),
                    composerState = rememberTextFieldState(),
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Write a reply").assertCountEquals(0)
    }

    @Test
    fun threadPhaseOffersTheComposer() {
        composeRule.setContent {
            MaterialTheme {
                ConversationDetail(
                    uiState = baseUiState.copy(phase = ConversationDetailPhase.Thread),
                    composerState = rememberTextFieldState(),
                    onLoadOlderRequested = {},
                    onSendTapped = {},
                )
            }
        }

        composeRule.onNodeWithText("Write a reply").assertIsDisplayed()
    }

    private companion object {
        val baseUiState = ConversationDetailUiState(
            phase = ConversationDetailPhase.Thread,
            canLoadOlder = false,
        )

        const val THREE_MINUTES_MILLIS = 3 * 60 * 1000L
        const val TWELVE_HOURS_MILLIS = 12 * 60 * 60 * 1000L
        const val SIXTEEN_HOURS_MILLIS = 16 * 60 * 60 * 1000L

        // 30 incoming replies spaced >2 minutes apart, so each forms its own MessageGroup —
        // more than enough rows to overflow the test viewport. Starting mid-day keeps every
        // reply (and the same-day send four hours later) on one local calendar day.
        fun viewportFillingHistory(dayStart: Long): List<ConversationReplyRow> = (1..30).map { index ->
            ConversationTestFixtures.replyRow(
                id = "history-$index",
                text = "History reply #$index",
                isOutgoing = false,
                sentAt = dayStart + TWELVE_HOURS_MILLIS + index * THREE_MINUTES_MILLIS,
            )
        }

        fun outgoingSend(sentAt: Long) = ConversationTestFixtures.replyRow(
            id = "sent-local",
            text = "Fresh outgoing reply",
            senderId = null,
            senderName = null,
            isOutgoing = true,
            sentAt = sentAt,
            syncState = ReplyEntity.SYNC_STATE_QUEUED,
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

        fun buildRepliesForAutoScrollTest(newestReplies: List<String>): List<ConversationReplyRow> =
            listOf(testReply("older", "Older reply")) + newestReplies.mapIndexed { index, text ->
                testReply(id = "newest-${index + 1}", text = text)
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
