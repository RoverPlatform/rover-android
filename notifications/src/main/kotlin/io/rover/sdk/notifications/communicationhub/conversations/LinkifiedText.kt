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
import java.util.regex.Pattern

/**
 * Matches a URL that begins with an explicit scheme, e.g. `https://...` or a custom
 * deep-link scheme such as `rv-product-support://...`. A scheme is a letter followed by
 * letters, digits, `+`, `-`, or `.` (per RFC 3986). [Linkify.WEB_URLS] only recognizes
 * http/https/rtsp schemes and bare domains, so custom deep-link schemes must be detected
 * separately.
 */
private val EXPLICIT_SCHEME_URL: Pattern =
    Pattern.compile("[a-zA-Z][a-zA-Z0-9+.\\-]*://\\S+")

/**
 * Sentence punctuation that is never part of a link when it appears at the end of a
 * match. Closing brackets are handled separately (see [urlEndAfterTrimming]) so that a
 * bracket balancing one inside the URL (e.g. `.../Foo_(bar)`) is retained.
 */
private const val ALWAYS_STRIP_TRAILING = ".,;:!?\"'>"

private val CLOSING_TO_OPENING_BRACKET = mapOf(')' to '(', ']' to '[', '}' to '{')

/**
 * The greedy `\S+` in [EXPLICIT_SCHEME_URL] captures trailing sentence punctuation and
 * enclosing brackets that are not really part of the URL. Returns the offset within
 * [url] at which the link should end: always-strip punctuation is removed, and a trailing
 * closing bracket is removed only when it is unbalanced (i.e. there is no matching opener
 * earlier in the URL), mirroring how NSDataDetector on iOS retains balanced brackets such
 * as `https://en.wikipedia.org/wiki/Foo_(bar)`.
 */
private fun urlEndAfterTrimming(url: String): Int {
    var end = url.length
    while (end > 0) {
        val c = url[end - 1]
        if (c in ALWAYS_STRIP_TRAILING) {
            end--
            continue
        }
        val opening = CLOSING_TO_OPENING_BRACKET[c]
        if (opening != null) {
            var opens = 0
            var closes = 0
            for (i in 0 until end) {
                when (url[i]) {
                    opening -> opens++
                    c -> closes++
                }
            }
            if (closes > opens) {
                end--
                continue
            }
        }
        break
    }
    return end
}

private data class DetectedLink(val url: String, val start: Int, val end: Int)

/**
 * Detects web URLs (including bare domains), email addresses, phone numbers, and
 * explicit-scheme URLs (including custom deep-link schemes such as
 * `rv-product-support://`) within plain reply text, producing an [AnnotatedString] with
 * tappable [LinkAnnotation.Url] annotations styled with [linkStyle]. Taps are delivered
 * to [onOpenUrl] with the detected URL (bare domains gain an http scheme, emails become
 * mailto:, phone numbers become tel:, explicit-scheme URLs are used verbatim).
 */
internal fun linkifyReplyText(
    text: String,
    linkStyle: SpanStyle,
    onOpenUrl: (String) -> Unit,
): AnnotatedString {
    val links = detectLinks(text)
    if (links.isEmpty()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        append(text)
        links.forEach { link ->
            addLink(
                url = LinkAnnotation.Url(
                    url = link.url,
                    styles = TextLinkStyles(style = linkStyle),
                    linkInteractionListener = { onOpenUrl(link.url) },
                ),
                start = link.start,
                end = link.end,
            )
        }
    }
}

private fun detectLinks(text: String): List<DetectedLink> {
    val result = mutableListOf<DetectedLink>()

    // First pass: URLs with an explicit scheme, including custom deep-link schemes
    // (e.g. rv-product-support://...) that Linkify.WEB_URLS does not recognize. These
    // take precedence so the full deep link is captured verbatim rather than having only
    // its bare-domain portion matched as a web URL.
    val matcher = EXPLICIT_SCHEME_URL.matcher(text)
    while (matcher.find()) {
        val start = matcher.start()
        val candidate = text.substring(start, matcher.end())
        val end = start + urlEndAfterTrimming(candidate)
        if (end > start) {
            result.add(DetectedLink(text.substring(start, end), start, end))
        }
    }

    // Second pass: web URLs, email addresses, and phone numbers, skipping any region
    // already covered by an explicit-scheme match above.
    val spannable = SpannableString(text)
    LinkifyCompat.addLinks(
        spannable,
        Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS,
    )
    spannable.getSpans(0, spannable.length, URLSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val overlaps = result.any { start < it.end && end > it.start }
        if (!overlaps) {
            result.add(DetectedLink(span.url, start, end))
        }
    }

    return result
}
