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

    private fun format8601WithTimeZone() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    override fun dateAsIso8601(date: Date, localTime: Boolean): String =
        if(localTime) format8601WithTimeZone().format(date) else format8601().format(date)


    override fun iso8601AsDate(iso8601Date: String, localTime: Boolean): Date = try {
        if(localTime) format8601WithTimeZone().parse(iso8601Date) else format8601().parse(iso8601Date)
    } catch (throwable: Throwable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            throw JSONException("Could not parse date '$iso8601Date' as ${if(localTime) "local" else "utc"}", throwable)
        } else {
            throw JSONException("Could not parse date '$iso8601Date' as ${if(localTime) "local" else "utc"}, because ${throwable.message}")
        }
    }
}
