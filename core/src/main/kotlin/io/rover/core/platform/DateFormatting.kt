package io.rover.core.platform

import android.os.Build
import org.json.JSONException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface DateFormattingInterface {
    fun dateAsIso8601(date: Date, localTime: Boolean = false): String

    fun iso8601AsDate(iso8601Date: String, localTime: Boolean = false): Date
}

class DateFormatting : DateFormattingInterface {
    // we must construct the date format objects lazily for each request, because they are not thread safe.
    private fun format8601() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val format8601WithTimeZone = if(
            Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP
    ) SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US) else
        // on legacy Android use the RFC 822 format, and below for outgoing values we'll transform
        // it to 8601.
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    override fun dateAsIso8601(date: Date, localTime: Boolean): String =
        if (localTime) {
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                // new shit
                format8601WithTimeZone.format(date)
            } else {
                // On legacy Android, we are using the RFC 822 (email) vs ISO 8601 date format, and
                // we use the following regex to transform it to something 8601 compatible≥
                format8601WithTimeZone.format(
                    date
                ).replace(Regex("(\\d\\d)(\\d\\d)$"), "$1:$2")
            }

        } else format8601().format(date)

    override fun iso8601AsDate(iso8601Date: String, localTime: Boolean): Date = try {
        iso8601Date.replace(Regex("Z$"), "+0000").let { transformed ->
            if (localTime) format8601WithTimeZone.parse(transformed) else format8601().parse(transformed)
        }
    } catch (throwable: Throwable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            throw JSONException("Could not parse date '$iso8601Date' as ${if (localTime) "local" else "utc"}", throwable)
        } else {
            throw JSONException("Could not parse date '$iso8601Date' as ${if (localTime) "local" else "utc"}, because ${throwable.message}")
        }
    }
}
