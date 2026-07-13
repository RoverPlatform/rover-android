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

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.text.util.LinkifyCompat

/**
 * Detects web URLs (including bare domains), email addresses, and phone numbers within
 * plain reply text, producing an [AnnotatedString] with tappable [LinkAnnotation.Url]
 * annotations styled with [linkStyle]. Taps are delivered to [onOpenUrl] with the
 * detected URL (bare domains gain an http scheme, emails become mailto:, phone numbers
 * become tel:).
 */
internal fun linkifyReplyText(
    text: String,
    linkStyle: SpanStyle,
    onOpenUrl: (String) -> Unit,
): AnnotatedString {
    val spannable = SpannableString(text)
    val foundLinks = LinkifyCompat.addLinks(
        spannable,
        Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS,
    )
    if (!foundLinks) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        append(text)
        spannable.getSpans(0, spannable.length, URLSpan::class.java).forEach { span ->
            addLink(
                url = LinkAnnotation.Url(
                    url = span.url,
                    styles = TextLinkStyles(style = linkStyle),
                    linkInteractionListener = { onOpenUrl(span.url) },
                ),
                start = spannable.getSpanStart(span),
                end = spannable.getSpanEnd(span),
            )
        }
    }
}
