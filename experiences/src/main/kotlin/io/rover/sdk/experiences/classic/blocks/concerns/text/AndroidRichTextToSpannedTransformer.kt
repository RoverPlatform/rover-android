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

package io.rover.sdk.experiences.classic.blocks.concerns.text

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import io.rover.sdk.experiences.platform.roverTextHtmlAsSpanned

internal class AndroidRichTextToSpannedTransformer : RichTextToSpannedTransformer {
    override fun transform(string: String, boldRelativeToBlockWeight: Font): Spanned {
        val spanned = string.roverTextHtmlAsSpanned()

        return if (spanned.isEmpty()) {
            spanned
        } else {
            // spans can arrive with a trailing newline; clip it off if present.
            if (spanned[spanned.lastIndex] == '\n') {
                spanned.delete(spanned.lastIndex, spanned.length)
            }

            // TextUtils.dumpSpans(spanned, lp, "  ")

            // we want the spanned bolds within the text to be relative to the base typeface'
            // for the entire text block.
            val styleSpans = spanned.getSpans(0, spanned.length, StyleSpan::class.java)
            val boldSpans = styleSpans.filter { it.style == Typeface.BOLD }
            // log.v("There are ${boldSpans.size} bolds for '${string}'")
            boldSpans.forEach {
                // replace the bold span with our own explicit typeface+style span.
                val start = spanned.getSpanStart(it)
                val end = spanned.getSpanEnd(it)

                // log.v("... bold span from $start to $end ('${spanned.substring(start, end)}')")

                // spanned.removeSpan(it)
                spanned.setSpan(
                    TypefaceAndExplicitBoldSpan(boldRelativeToBlockWeight.fontFamily, boldRelativeToBlockWeight.fontStyle),
                    start,
                    end,
                    0
                )
            }

            spanned
        }
    }
}
