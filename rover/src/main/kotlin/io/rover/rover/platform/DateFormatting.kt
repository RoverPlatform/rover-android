package io.rover.rover.platform

import java.text.SimpleDateFormat
import java.util.*

interface DateFormattingInterface {
    fun dateAsIso8601(date: Date): String

    fun iso8601AsDate(iso8601Date: String): Date
}

class DateFormatting : DateFormattingInterface {
    private val format8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun dateAsIso8601(date: Date): String = format8601.format(date)

    override fun iso8601AsDate(iso8601Date: String): Date = format8601.parse(iso8601Date)
}
