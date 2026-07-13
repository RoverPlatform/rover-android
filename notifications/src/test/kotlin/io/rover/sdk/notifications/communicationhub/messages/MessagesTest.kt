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
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.rover.sdk.notifications.communicationhub.messages

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import io.rover.sdk.notifications.communicationhub.posts.PostEntity
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessagesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collapsedMixedFeedHasDividerOnlyBetweenRows() {
        renderMessages(rows = mixedRows, isExpanded = false)

        assertDividerCount(mixedRows.size - 1)
    }

    @Test
    fun expandedMixedSearchResultsHaveDividerOnlyBetweenRows() {
        renderMessages(rows = mixedRows, isExpanded = true)

        assertDividerCount(mixedRows.size - 1)
    }

    @Test
    fun singleRowHasNoDivider() {
        renderMessages(rows = listOf(postRow("post-1")), isExpanded = false)

        assertDividerCount(0)
    }

    @Test
    fun emptyFeedHasNoDivider() {
        renderMessages(rows = emptyList(), isExpanded = false)

        assertDividerCount(0)
    }

    private fun renderMessages(rows: List<MessageFeedRow>, isExpanded: Boolean) {
        composeRule.setContent {
            MaterialTheme {
                Messages(
                    rows = rows,
                    searchQuery = if (isExpanded) "search" else "",
                    displayTitle = "Messages",
                    isExpanded = isExpanded,
                    onSearchQueryChanged = {},
                    onExpandedChanged = {},
                    onPostClick = {},
                    onConversationClick = {},
                    onRefresh = {},
                    isRefreshing = false,
                )
            }
        }
    }

    private fun assertDividerCount(expected: Int) {
        assertEquals(
            expected,
            composeRule
                .onAllNodesWithTag(MESSAGE_ROW_DIVIDER_TEST_TAG)
                .fetchSemanticsNodes()
                .size,
        )
    }

    private fun postRow(id: String): MessageFeedRow.Post = MessageFeedRow.Post(
        PostWithSubscription(
            post = PostEntity(
                id = id,
                subject = "Post $id",
                previewText = "Post preview",
                receivedAt = 1_000L,
                url = null,
                isRead = false,
                coverImageURL = null,
                subscriptionId = null,
            ),
            subscription = null,
        )
    )

    private fun conversationRow(id: String): MessageFeedRow.Conversation = MessageFeedRow.Conversation(
        id = id,
        senderName = "Sender",
        senderAvatarUrl = null,
        subject = "Conversation $id",
        preview = "Conversation preview",
        timestamp = 2_000L,
        isUnread = true,
    )

    private val mixedRows = listOf(
        postRow("post-1"),
        conversationRow("conversation-1"),
        postRow("post-2"),
    )
}
