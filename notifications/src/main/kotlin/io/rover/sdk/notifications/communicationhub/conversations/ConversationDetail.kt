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

import android.app.Activity
import android.content.res.Configuration
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.tooling.preview.Preview
import io.rover.sdk.notifications.communicationhub.messages.formatReplyClockTime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.Date

internal fun initialsFor(name: String?): String =
    name
        ?.split(" ")
        ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
        ?.take(2)
        ?.joinToString("")
        .orEmpty()
        .ifEmpty { "?" }

@Composable
internal fun ConversationDetail(
    uiState: ConversationDetailUiState,
    onComposerTextChanged: (String) -> Unit,
    onLoadOlderRequested: () -> Unit,
    onSendTapped: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit = {},
) {
    // Compose's imePadding() requires the window to NOT resize for the keyboard — it is the
    // sole mechanism for shifting the layout. Modern host activities already guarantee this
    // (ADJUST_NOTHING + setDecorFitsSystemWindows(false)), but legacy hosts default to
    // ADJUST_RESIZE/UNSPECIFIED, which causes the OS to resize the window AND report the full
    // IME height via WindowInsets.ime simultaneously, doubling the shift. By temporarily forcing
    // ADJUST_NOTHING for any host while this screen is active, both setups converge on the
    // same behaviour: window stays fixed, imePadding() is the only mover. The original mode
    // is restored on dispose so other screens in the host activity are unaffected.
    // Re-applied on every window focus gain because Android resets the window's softInputMode
    // after onResume() during window focus restoration — ON_RESUME fires too early to catch it.
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? Activity
        val window = activity?.window
        val lifecycleOwner = activity as? LifecycleOwner
        val originalSoftInputMode = window?.attributes?.softInputMode ?: 0
        @Suppress("DEPRECATION")
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                @Suppress("DEPRECATION")
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                @Suppress("DEPRECATION")
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            @Suppress("DEPRECATION")
            window?.setSoftInputMode(originalSoftInputMode)
        }
    }

    val listState = rememberLazyListState()
    val currentUiState by rememberUpdatedState(uiState)

    // In reversed layout, index 0 is the visual bottom. When a new message arrives
    // and the user is already at the bottom, scroll to keep them pinned there.
    val newestRenderedRowSignature = uiState.threadRows.lastOrNull()?.signature()
    LaunchedEffect(newestRenderedRowSignature) {
        if (newestRenderedRowSignature != null && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    val density = LocalDensity.current
    val thresholdPx = with(density) { 200.dp.toPx() }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
                ?: return@snapshotFlow Float.MAX_VALUE
            val renderedRowCount = info.totalItemsCount
            // Quick pre-filter: skip expensive math when far from the visual top
            if (renderedRowCount - lastVisible.index > 5) return@snapshotFlow Float.MAX_VALUE
            // In reversed layout, the last visible item is nearest the visual top.
            // Compute remaining pixels between the last visible item's edge and the
            // end of content (i.e. the visual top boundary).
            val lastItemEnd = lastVisible.offset + lastVisible.size
            val viewportEnd = info.viewportEndOffset
            (viewportEnd - lastItemEnd).toFloat()
        }
            .map { it < thresholdPx }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (currentUiState.canLoadOlder && !currentUiState.isLoadingOlder) {
                    onLoadOlderRequested()
                }
            }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val newestGroupId = uiState.threadRows
                        .lastOrNull { it is ConversationThreadRow.MessageGroup }
                        ?.let { (it as ConversationThreadRow.MessageGroup).id }
                    items(uiState.threadRows.asReversed(), key = { threadRowKey(it) }) { row ->
                        when (row) {
                            is ConversationThreadRow.DayDivider -> DayDividerRow(row)
                            is ConversationThreadRow.MessageGroup ->
                                MessageGroupRow(row = row, isMostRecentGroup = row.id == newestGroupId, onOpenUrl = onOpenUrl)
                        }
                    }

                    if (uiState.isLoadingOlder) {
                        item(key = "loading-older-spinner") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .testTag("loading-older-spinner"),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }

            Composer(
                text = uiState.composerText,
                onTextChanged = onComposerTextChanged,
                onSendTapped = onSendTapped,
                isSending = uiState.isSending,
            )
        }
    }
}

@Composable
private fun AvatarCircle(name: String?, avatarUrl: String?) {
    val size = 34.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initialsFor(name),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun DayDividerRow(row: ConversationThreadRow.DayDivider) {
    Text(
        text = row.dateLabel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MessageGroupRow(
    row: ConversationThreadRow.MessageGroup,
    isMostRecentGroup: Boolean,
    onOpenUrl: (String) -> Unit = {},
) {
    val groupHasQueuedReply = row.replies.hasQueuedReply()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        row.replies.forEachIndexed { index, reply ->
            ReplyBubble(
                reply = reply,
                showChrome = index == 0,
                isFirstInGroup = index == 0,
                isLastInGroup = index == row.replies.lastIndex,
                statusLabel = replyStatusLabelFor(
                    reply = reply,
                    isLastInGroup = index == row.replies.lastIndex,
                    isMostRecentGroup = isMostRecentGroup,
                    groupHasQueuedReply = groupHasQueuedReply,
                ),
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

// Bubbles in the same group share rounding: the outer edge stays fully rounded, while the
// aligned (inner) edge squares off where a bubble has an adjacent neighbour. Mirrors iOS.
private val BUBBLE_RADIUS_LARGE = 20.dp
private val BUBBLE_RADIUS_SMALL = 4.dp

private fun replyBubbleShape(
    isOutgoing: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
): RoundedCornerShape =
    if (isOutgoing) {
        // Outgoing (fan) bubbles align to the trailing/end edge.
        RoundedCornerShape(
            topStart = BUBBLE_RADIUS_LARGE,
            bottomStart = BUBBLE_RADIUS_LARGE,
            topEnd = if (isFirstInGroup) BUBBLE_RADIUS_LARGE else BUBBLE_RADIUS_SMALL,
            bottomEnd = if (isLastInGroup) BUBBLE_RADIUS_LARGE else BUBBLE_RADIUS_SMALL,
        )
    } else {
        // Incoming (member) bubbles align to the leading/start edge.
        RoundedCornerShape(
            topEnd = BUBBLE_RADIUS_LARGE,
            bottomEnd = BUBBLE_RADIUS_LARGE,
            topStart = if (isFirstInGroup) BUBBLE_RADIUS_LARGE else BUBBLE_RADIUS_SMALL,
            bottomStart = if (isLastInGroup) BUBBLE_RADIUS_LARGE else BUBBLE_RADIUS_SMALL,
        )
    }

@Composable
private fun ReplyBubble(
    reply: ConversationReplyRow,
    showChrome: Boolean,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    statusLabel: ReplyStatusLabel?,
    onOpenUrl: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (reply.isOutgoing) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (reply.isOutgoing) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!reply.isOutgoing) {
                if (showChrome) {
                    AvatarCircle(name = reply.senderName, avatarUrl = reply.senderAvatarUrl)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.width(42.dp))
                }
            }
            Surface(
                modifier = Modifier.weight(1f, fill = false),
                shape = replyBubbleShape(
                    isOutgoing = reply.isOutgoing,
                    isFirstInGroup = isFirstInGroup,
                    isLastInGroup = isLastInGroup,
                ),
                color = if (reply.isOutgoing) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    reply.senderName?.takeIf { !reply.isOutgoing && showChrome }?.let { senderName ->
                        Text(
                            text = senderName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    reply.content.forEach { block ->
                        when {
                            block.type.equals(ReplyContentBlock.TYPE_TEXT, ignoreCase = true) && !block.text.isNullOrBlank() -> {
                                val linkStyle = SpanStyle(
                                    color = if (reply.isOutgoing) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    textDecoration = TextDecoration.Underline,
                                )
                                // The annotation's tap listener outlives this recomposition (remember is
                                // keyed only on text/style), so read the latest callback through state.
                                val currentOnOpenUrl by rememberUpdatedState(onOpenUrl)
                                val linkifiedText = remember(block.text, linkStyle) {
                                    linkifyReplyText(block.text, linkStyle) { url -> currentOnOpenUrl(url) }
                                }
                                Text(
                                    text = linkifiedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (reply.isOutgoing) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            block.type.equals(ReplyContentBlock.TYPE_IMAGE, ignoreCase = true) && block.url != null -> {
                                SubcomposeAsyncImage(
                                    model = block.url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit,
                                ) {
                                    when (painter.state) {
                                        is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                                        else -> Box(
                                            modifier = Modifier
                                                .size(width = 220.dp, height = 140.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    RoundedCornerShape(8.dp),
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        when (statusLabel) {
            ReplyStatusLabel.Failed -> Text(
                text = "Not Delivered",
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            ReplyStatusLabel.Sending -> Text(
                text = "Sending…",
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ReplyStatusLabel.Timestamp -> Text(
                text = formatReplyClockTime(Date(statusLabel.sentAtMillis)),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            null -> Unit
        }
    }
}

private fun threadRowKey(row: ConversationThreadRow): String = when (row) {
    is ConversationThreadRow.DayDivider -> "day:${row.dayStartMillis}"
    is ConversationThreadRow.MessageGroup -> "group:${row.id}"
}

private fun ConversationThreadRow.signature(): String = when (this) {
    is ConversationThreadRow.DayDivider -> "day:${dayStartMillis}"
    is ConversationThreadRow.MessageGroup ->
        replies.joinToString(separator = ",", prefix = "group:${id}:") { "${it.id}:${it.syncState.orEmpty()}" }
}

// region Previews

@Preview(name = "AvatarCircle — initials")
@Composable
private fun AvatarCircleInitialsPreview() {
    MaterialTheme { AvatarCircle(name = "Morgan Lee", avatarUrl = null) }
}

@Preview(name = "DayDivider")
@Composable
private fun DayDividerPreview() {
    MaterialTheme {
        DayDividerRow(ConversationThreadRow.DayDivider(dateLabel = "Today", dayStartMillis = 0L))
    }
}

@Preview(name = "ReplyBubble — incoming, with chrome")
@Composable
private fun ReplyBubbleIncomingWithChromePreview() {
    MaterialTheme {
        ReplyBubble(
            reply = previewReply("1", "Hey! How can I help you today?", isOutgoing = false, senderName = "Morgan Lee"),
            showChrome = true,
            isFirstInGroup = true,
            isLastInGroup = true,
            statusLabel = null,
        )
    }
}

@Preview(name = "ReplyBubble — incoming, grouped")
@Composable
private fun ReplyBubbleIncomingGroupedPreview() {
    MaterialTheme {
        ReplyBubble(
            reply = previewReply("2", "We can look into that for you right now.", isOutgoing = false, senderName = "Morgan Lee"),
            showChrome = false,
            isFirstInGroup = false,
            isLastInGroup = true,
            statusLabel = null,
        )
    }
}

@Preview(name = "ReplyBubble — outgoing")
@Composable
private fun ReplyBubbleOutgoingPreview() {
    MaterialTheme {
        ReplyBubble(
            reply = previewReply("3", "I need help with my ticket from last week", isOutgoing = true),
            showChrome = false,
            isFirstInGroup = true,
            isLastInGroup = true,
            statusLabel = ReplyStatusLabel.Sending,
        )
    }
}

@Preview(name = "ReplyBubble — multi-block (text + image)")
@Composable
private fun ReplyBubbleMultiBlockPreview() {
    MaterialTheme {
        ReplyBubble(
            reply = ConversationReplyRow(
                id = "4",
                senderId = "participant-1",
                senderName = "Morgan Lee",
                senderAvatarUrl = null,
                content = listOf(
                    ReplyContentBlock(type = ReplyContentBlock.TYPE_TEXT, text = "Here's the screenshot you asked for:", url = null),
                    ReplyContentBlock(type = ReplyContentBlock.TYPE_IMAGE, text = null, url = "https://example.com/screenshot.png"),
                ),
                sentAt = 0L,
                externalID = null,
                isOutgoing = false,
            ),
            showChrome = true,
            isFirstInGroup = true,
            isLastInGroup = true,
            statusLabel = null,
        )
    }
}

@Preview(name = "MessageGroup — multiple replies")
@Composable
private fun MessageGroupRowPreview() {
    MaterialTheme {
        MessageGroupRow(
            row = ConversationThreadRow.MessageGroup(
                id = "group-1",
                senderKey = "participant-1",
                senderName = "Morgan Lee",
                senderAvatarUrl = null,
                isOutgoing = false,
                replies = listOf(
                    previewReply("1", "Hey! How can I help you today?", isOutgoing = false, senderName = "Morgan Lee"),
                    previewReply("2", "We can look into that for you right now.", isOutgoing = false, senderName = "Morgan Lee"),
                    previewReply("3", "Just give me a moment.", isOutgoing = false, senderName = "Morgan Lee"),
                ),
            ),
            isMostRecentGroup = true,
        )
    }
}

@Preview(name = "Composer — empty")
@Composable
private fun ComposerEmptyPreview() {
    MaterialTheme { Composer(text = "", onTextChanged = {}, onSendTapped = {}, isSending = false) }
}

@Preview(name = "Composer — with text")
@Composable
private fun ComposerWithTextPreview() {
    MaterialTheme { Composer(text = "I need help with my order", onTextChanged = {}, onSendTapped = {}, isSending = false) }
}

@Preview(name = "Composer — sending")
@Composable
private fun ComposerSendingPreview() {
    MaterialTheme { Composer(text = "I need help with my order", onTextChanged = {}, onSendTapped = {}, isSending = true) }
}

@Preview(name = "ConversationDetail — loading")
@Composable
private fun ConversationDetailLoadingPreview() {
    MaterialTheme {
        ConversationDetail(
            uiState = ConversationDetailUiState(
                isLoading = true,
            ),
            onComposerTextChanged = {},
            onLoadOlderRequested = {},
            onSendTapped = {},
        )
    }
}

@Preview(name = "ConversationDetail — thread")
@Preview(name = "ConversationDetail — thread (dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConversationDetailThreadPreview() {
    val now = System.currentTimeMillis()
    val yesterday = now - 24 * 60 * 60 * 1000L
    val replies = listOf(
        previewReply("y1", "Hi, I bought a ticket last week but never got a confirmation email.", isOutgoing = true,  sentAt = yesterday),
        previewReply("y2", "Hi there! Let me look that up for you.", isOutgoing = false, senderName = "Morgan Lee", sentAt = yesterday + 2 * 60 * 1000L),
        previewReply("y3", "Can you confirm the email address you used at checkout?", isOutgoing = false, senderName = "Morgan Lee", sentAt = yesterday + 3 * 60 * 1000L),
        previewReply("t1", "Sure, it's fan@example.com", isOutgoing = true, sentAt = now - 30 * 60 * 1000L),
        previewReply("t2", "Found it! Your confirmation was sent to that address.", isOutgoing = false, senderName = "Morgan Lee", sentAt = now - 28 * 60 * 1000L),
        previewReply("t3", "It looks like it may have landed in your spam folder.", isOutgoing = false, senderName = "Morgan Lee", sentAt = now - 28 * 60 * 1000L + 30_000L),
        previewReply("t4", "I've resent it — you should receive it shortly.", isOutgoing = false, senderName = "Morgan Lee", sentAt = now - 28 * 60 * 1000L + 60_000L),
        previewReply("t5", "Got it, thank you!", isOutgoing = true, sentAt = now - 5 * 60 * 1000L),
    )
    MaterialTheme {
        ConversationDetail(
            uiState = ConversationDetailUiState(
                threadRows = buildConversationThreadRows(replies),
            ),
            onComposerTextChanged = {},
            onLoadOlderRequested = {},
            onSendTapped = {},
        )
    }
}

private fun previewReply(
    id: String,
    text: String,
    isOutgoing: Boolean,
    senderName: String? = null,
    sentAt: Long = 0L,
) = ConversationReplyRow(
    id = id,
    senderId = if (isOutgoing) null else "participant-1",
    senderName = senderName,
    senderAvatarUrl = null,
    content = listOf(ReplyContentBlock(type = ReplyContentBlock.TYPE_TEXT, text = text, url = null)),
    sentAt = sentAt,
    externalID = null,
    isOutgoing = isOutgoing,
)

// endregion

@Composable
private fun Composer(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendTapped: () -> Unit,
    isSending: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                placeholder = { Text("Write a reply") },
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                ),
            )
            FilledIconButton(
                onClick = onSendTapped,
                enabled = text.isNotBlank() && !isSending,
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
