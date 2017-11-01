package io.rover.rover.platform

import android.os.Build
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ParagraphStyle
import io.rover.rover.core.logging.log

fun String.roverTextHtmlAsSpanned(): Spanned {
    return if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        val spannedBuilder = Html.fromHtml(this) as SpannableStringBuilder
        // the legacy version of android.text.Html (ie without
        // Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH), Html.fromHtml() returns one additional
        // excessive newline (specifically, a \n character) per each paragraph in the source HTML.
        // So, for every run of newlines in the output spannable, we want to remove one of the
        // newlines.

        val indexesOfSuperfluousNewlines = spannedBuilder.foldIndexed(listOf<Int>()) { index, accumulatedIndexes, character ->
            if(character != '\n' && index > 0 && spannedBuilder[index - 1] == '\n') {
                // a sequence of \n's is terminating.  drop the last one.
                accumulatedIndexes + (index - 1)
            } else {
                accumulatedIndexes
            }
        }

        // now to remove all of these discovered extra newlines.
        var deletionDeflection = 0
        indexesOfSuperfluousNewlines.forEach {
            log.v("Deleting index $it")
            // mutate the spanned builder.
            spannedBuilder.delete(it + deletionDeflection, it + deletionDeflection + 1)
            deletionDeflection -= 1
        }

        spannedBuilder
    } else {
        Html.fromHtml(this, Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)
    }
}

private fun <T> Spanned.orderedSpans(spanClass: Class<T>): List<T> {
    return orderedSpansX(spanClass, 0, listOf())
}

tailrec fun <T> Spanned.orderedSpansX(
    spanClass: Class<T>,
    startIndex: Int,
    spans: List<T>
): List<T> {
    val nextTransitionIndex = this.nextSpanTransition(startIndex, this.lastIndex, spanClass)

    val foundSpans = this.getSpans(startIndex, nextTransitionIndex, spanClass)

    // TODO: the concat operation here is causing unnecessary copies
    return this.orderedSpansX(spanClass, nextTransitionIndex, spans + foundSpans)
}