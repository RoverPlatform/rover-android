@file:JvmName("StringExtensions")

package io.rover.sdk.platform

import android.os.Build
import android.text.Html
import android.text.SpannableStringBuilder

internal fun String.roverTextHtmlAsSpanned(): SpannableStringBuilder {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        @Suppress("DEPRECATION")
        val spannedBuilder = Html.fromHtml(this) as SpannableStringBuilder
        // the legacy version of android.text.Html (ie without
        // Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH), Html.fromHtml() returns one additional
        // excessive newline (specifically, a \n character) per each paragraph in the source HTML.
        // So, for every run of newlines in the output spannable, we want to remove one of the
        // newlines.

        val indexesOfSuperfluousNewlines = spannedBuilder.foldIndexed(listOf<Int>()) { index, accumulatedIndexes, character ->
            if (character != '\n' && index > 0 && spannedBuilder[index - 1] == '\n') {
                // a sequence of \n's is terminating.  drop the last one.
                // TODO: the append operation here is causing unnecessary copies
                accumulatedIndexes + (index - 1)
            } else {
                accumulatedIndexes
            }
        }

        // now to remove all of these discovered extra newlines.
        var deletionDeflection = 0
        indexesOfSuperfluousNewlines.forEach {
            // mutate the spanned builder.
            spannedBuilder.delete(it + deletionDeflection, it + deletionDeflection + 1)
            deletionDeflection -= 1
        }

        spannedBuilder
    } else {
        Html.fromHtml(this.replace("<p></p>", "<p>&nbsp;</p>"), Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH) as SpannableStringBuilder
    }
}
