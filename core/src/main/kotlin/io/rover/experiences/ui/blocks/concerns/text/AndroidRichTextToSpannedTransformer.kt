package io.rover.experiences.ui.blocks.concerns.text

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import io.rover.core.platform.roverTextHtmlAsSpanned

class AndroidRichTextToSpannedTransformer : RichTextToSpannedTransformer {
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
