package io.rover.rover.platform

import android.os.Build
import android.text.Html
import android.text.Spanned
import io.rover.rover.core.logging.log

fun String.simpleHtmlAsSpanned(): Spanned {
    return if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {

    // I basically wanna reduce any sequence of newlines by 1 (because for some reason newlines
    // always come in 2 or more.)

        val newlinesPruned = this.lines().foldRight(listOf<String>()) { line, accumulated ->
            // peek backwards:


            // identify when a sequence of newlines is *terminating* (ie., line is not empty) but there are more than 0 accumulated newlines. If so,
            // knock the one item off the end of the accumulator.
            val alreadyAccumulatedNewlines = accumulated.takeLastWhile { it.isEmpty() }.count()

            if(line.isEmpty() && alreadyAccumulatedNewlines > 0) {
                // sequence of newlines is terminating.  remove one of the empty newlines and add the
                // additional line.
                accumulated.subList(0, accumulated.lastIndex - 1) + line
            }
            accumulated + line
        }.joinToString("\n")
        Html.fromHtml(newlinesPruned)
    } else {
        Html.fromHtml(this, Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)
    }.apply { log.v("READ HTML STRING '${this}'")}
}
