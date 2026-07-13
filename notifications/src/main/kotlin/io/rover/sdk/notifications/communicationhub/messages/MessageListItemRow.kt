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

package io.rover.sdk.notifications.communicationhub.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Date

/**
 * The shape that should be used for the avatar/icon shown on message rows.
 */
internal enum class MessageAvatarShape {
    /**
     * A circle.
     */
    Participant,

    /**
     * A squirqle.
     */
    Subscription,
}

@Composable
internal fun MessageListItemRow(
    isRead: Boolean,
    senderName: String?,
    subject: String?,
    preview: String?,
    timestamp: Long,
    avatarUrl: String?,
    avatarShape: MessageAvatarShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foregroundColor = if (isRead) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val fontWeight = if (isRead) FontWeight.Normal else FontWeight.SemiBold
    val formattedTimestamp = formatMessageTimestamp(Date(timestamp))

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                stateDescription = if (isRead) "Read" else "Unread"
            },
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .size(width = 10.dp, height = 8.dp),
            ) {
                if (!isRead) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }

            Avatar(
                senderName = senderName,
                avatarUrl = avatarUrl,
                shape = avatarShape,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                senderName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { sender ->
                        Text(
                            text = sender,
                            color = foregroundColor,
                            fontWeight = fontWeight,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                subject
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { rowSubject ->
                        Text(
                            text = rowSubject,
                            color = foregroundColor,
                            fontWeight = fontWeight,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                preview
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { rowPreview ->
                        Text(
                            text = rowPreview,
                            color = foregroundColor,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }

            Text(
                text = formattedTimestamp,
                color = foregroundColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Avatar(
    senderName: String?,
    avatarUrl: String?,
    shape: MessageAvatarShape,
) {
    val avatarShape = when (shape) {
        MessageAvatarShape.Participant -> CircleShape
        MessageAvatarShape.Subscription -> RoundedCornerShape(10.dp)
    }
    val placeholder = senderName
        ?.trim()
        ?.firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        .orEmpty()

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(avatarShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (placeholder.isNotEmpty()) {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
