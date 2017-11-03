package io.rover.rover.ui

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import io.rover.rover.core.logging.log
import io.rover.rover.platform.roverTextHtmlAsSpanned
import io.rover.rover.ui.types.Font
import io.rover.rover.ui.viewmodels.TextBlockViewModel
import io.rover.rover.ui.views.TypefaceAndExplicitBoldSpan

/**
 * Transform a Rover HTML-decorated rich text string (as seen in Text blocks).
 *
 * This logic is kept outside of the [TextBlockViewModel] because it has runtime Android
 * dependencies.
 */
interface RichTextToSpannedTransformer {
    fun transform(string: String, boldRelativeToBlockWeight: Font): Spanned
}

class AndroidRichTextToSpannedTransformer: RichTextToSpannedTransformer {
    override fun transform(string: String, boldRelativeToBlockWeight: Font): Spanned {
        val spanned = string.roverTextHtmlAsSpanned()

        // spans always arrive with a trailing newline; clip it off.
        spanned.delete(spanned.lastIndex, spanned.length)

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

        return spanned
    }
}
