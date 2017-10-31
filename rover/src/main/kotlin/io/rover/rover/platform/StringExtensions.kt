package io.rover.rover.platform

import android.os.Build
import android.text.Html
import android.text.Spanned

fun String.simpleHtmlAsSpanned(): Spanned {
    return if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        Html.fromHtml(this)
    } else {
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    }
}